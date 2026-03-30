"""
Quiz cog — /quiz [lesson] [count] slash command.

Flow:
  1. /quiz [lesson] [count] — generate N questions from Neo4j concept graph
  2. Each question appears as an embed with answer buttons:
     - Multiple choice / true-false → option buttons
     - Open question → "Odpowiedz" button opening a text modal
  3. User's answer is evaluated via graph-RAG and LLM
  4. Evaluation embed shows score, feedback, correct answer, and sources
  5. After all questions a summary embed closes the session

Reuses quiz_agent.py: generate_question(), evaluate_answer()
"""
from __future__ import annotations

import asyncio
import logging
import random
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

ROOT = Path(__file__).resolve().parent.parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from discord_bot.bot_auth import check_access  # noqa: E402 — must come after sys.path fixup

if TYPE_CHECKING:
    from discord_bot.bot import MindForgeBot

log = logging.getLogger(__name__)

MAX_QUESTIONS = 20
DEFAULT_QUESTIONS = 5
INTERACTION_TIMEOUT = 600  # seconds — how long to wait for an answer
EMBED_CHAR_LIMIT = 4096

_OWNERSHIP_DENIAL = (
    "❌ Tylko osoba, która uruchomiła tę sesję, może z niej korzystać."
)


# ── Session data models ─────────────────────────────────────────────


@dataclass
class _QuizQuestion:
    question: str
    topic: str
    question_type: str  # "open" | "multiple_choice" | "true_false"
    options: list[str] | None
    context: str
    source_lessons: list[str]
    reference_answer: str = ""  # generated at question-creation time; never sent to clients


@dataclass
class _QuizSession:
    user_id: int
    questions: list[_QuizQuestion]
    current_index: int = 0
    evaluations: list[dict[str, Any]] = field(default_factory=list)


# ── Embed builders ──────────────────────────────────────────────────


def _score_color(score: float) -> discord.Color:
    if score >= 0.7:
        return discord.Color.green()
    if score >= 0.4:
        return discord.Color.orange()
    return discord.Color.red()


def _build_question_embed(q: _QuizQuestion, number: int, total: int) -> discord.Embed:
    embed = discord.Embed(
        title=f"Pytanie {number}/{total}",
        description=q.question[:EMBED_CHAR_LIMIT],
        color=discord.Color.blurple(),
    )
    embed.set_footer(text=f"Temat: {q.topic[:100]}  •  Typ: {q.question_type}")
    if q.source_lessons:
        embed.add_field(
            name="Źródło",
            value=", ".join(q.source_lessons[:5]),
            inline=False,
        )
    return embed


def _build_evaluation_embed(
    score: float,
    feedback: str,
    correct_answer: str,
    grounding_sources: list[str],
    user_answer: str,
    question_number: int,
    total: int,
) -> discord.Embed:
    pct = int(score * 100)
    embed = discord.Embed(
        title=f"Ocena odpowiedzi ({question_number}/{total}) — {pct}%",
        color=_score_color(score),
    )
    embed.add_field(
        name="Twoja odpowiedź",
        value=user_answer[:512] or "—",
        inline=False,
    )
    embed.add_field(name="Feedback", value=feedback[:1024], inline=False)
    embed.add_field(
        name="Wzorcowa odpowiedź",
        value=correct_answer[:512] or "—",
        inline=False,
    )
    if grounding_sources:
        embed.add_field(
            name="Źródła",
            value=", ".join(grounding_sources[:5]),
            inline=False,
        )
    return embed


def _build_summary_embed(session: _QuizSession) -> discord.Embed:
    total = len(session.questions)
    answered = len(session.evaluations)
    avg = (
        sum(e["score"] for e in session.evaluations) / answered if answered else 0.0
    )
    pct = int(avg * 100)

    embed = discord.Embed(
        title="📊 Podsumowanie sesji quiz",
        description=(
            f"Odpowiedzi: **{answered}/{total}**  •  Średni wynik: **{pct}%**"
        ),
        color=_score_color(avg),
    )
    for i, ev in enumerate(session.evaluations, 1):
        score_pct = int(ev["score"] * 100)
        embed.add_field(
            name=f"Pytanie {i}: {ev['topic'][:50]}",
            value=f"Wynik: {score_pct}% — {ev['feedback'][:200]}",
            inline=False,
        )
    return embed


# ── UI components ────────────────────────────────────────────────────


class AnswerModal(discord.ui.Modal, title="Twoja odpowiedź"):
    """Text input modal for open-ended quiz questions."""

    answer_input: discord.ui.TextInput = discord.ui.TextInput(
        label="Odpowiedź",
        style=discord.TextStyle.paragraph,
        placeholder="Wpisz swoją odpowiedź…",
        max_length=2000,
        required=True,
    )

    def __init__(self, cog: "QuizCog", user_id: int) -> None:
        super().__init__(timeout=INTERACTION_TIMEOUT)
        self.cog = cog
        self.user_id = user_id

    async def on_submit(self, interaction: discord.Interaction) -> None:
        if interaction.user.id != self.user_id:
            await interaction.response.send_message(_OWNERSHIP_DENIAL, ephemeral=True)
            return
        # Defer the modal response so we can send follow-up messages
        await interaction.response.defer()
        await self.cog._handle_answer(
            interaction, self.user_id, self.answer_input.value
        )


class QuizView(discord.ui.View):
    """Buttons for a single quiz question (MC buttons or an open-answer button)."""

    def __init__(
        self,
        cog: "QuizCog",
        user_id: int,
        question: _QuizQuestion,
    ) -> None:
        super().__init__(timeout=INTERACTION_TIMEOUT)
        self.cog = cog
        self.user_id = user_id
        self._setup_buttons(question)

    # ── Button setup ────────────────────────────────────────────────

    def _setup_buttons(self, question: _QuizQuestion) -> None:
        q_type = question.question_type
        options = question.options

        if q_type in ("multiple_choice", "true_false") and options:
            for option in options:
                btn = discord.ui.Button(
                    label=option[:80],
                    style=discord.ButtonStyle.secondary,
                )
                btn.callback = self._make_option_callback(option)
                self.add_item(btn)
        else:
            open_btn = discord.ui.Button(
                label="Odpowiedz",
                style=discord.ButtonStyle.primary,
                emoji="✏️",
            )
            open_btn.callback = self._open_answer_callback
            self.add_item(open_btn)

    def _make_option_callback(self, option: str):
        """Create a button callback that submits the selected option as the answer."""

        async def callback(interaction: discord.Interaction) -> None:
            if interaction.user.id != self.user_id:
                await interaction.response.send_message(_OWNERSHIP_DENIAL, ephemeral=True)
                return
            # Disable all buttons to prevent re-clicking
            for child in self.children:
                if isinstance(child, discord.ui.Button):
                    child.disabled = True
            self.stop()
            # Edit the message to reflect the disabled state
            await interaction.response.edit_message(view=self)
            # Evaluate the selected option
            await self.cog._handle_answer(interaction, self.user_id, option)

        return callback

    async def _open_answer_callback(self, interaction: discord.Interaction) -> None:
        """Open a text modal when the user wants to type a free-form answer."""
        if interaction.user.id != self.user_id:
            await interaction.response.send_message(_OWNERSHIP_DENIAL, ephemeral=True)
            return
        modal = AnswerModal(self.cog, self.user_id)
        await interaction.response.send_modal(modal)
        self.stop()

    async def on_timeout(self) -> None:
        # Disable buttons after timeout (best-effort; we may not have the message ref)
        for child in self.children:
            if isinstance(child, discord.ui.Button):
                child.disabled = True


# ── Cog ─────────────────────────────────────────────────────────────


class QuizCog(commands.Cog, name="Quiz"):
    """Interactive quiz powered by graph-RAG."""

    def __init__(self, bot: "MindForgeBot") -> None:
        self.bot = bot
        # Active sessions keyed by Discord user ID
        self.sessions: dict[int, _QuizSession] = {}

    # ── Slash command ────────────────────────────────────────────────

    @app_commands.command(
        name="quiz",
        description="Interaktywny quiz na podstawie lekcji z grafu wiedzy",
    )
    @app_commands.describe(
        lesson="Filtr lekcji, np. S01E01 (domyślnie: wszystkie lekcje)",
        count="Liczba pytań, 1–20 (domyślnie: 5)",
    )
    async def quiz(
        self,
        interaction: discord.Interaction,
        lesson: str | None = None,
        count: int = DEFAULT_QUESTIONS,
    ) -> None:
        if not await check_access(interaction):
            return
        if self.bot.neo4j_driver is None:
            await interaction.response.send_message(
                "❌ Graph-RAG nie jest dostępny. "
                "Ustaw `ENABLE_GRAPH_RAG=true` w `.env` i uruchom Neo4j.",
                ephemeral=True,
            )
            return

        count = max(1, min(count, MAX_QUESTIONS))

        # Clean up any lingering session for this user
        self.sessions.pop(interaction.user.id, None)

        # Defer while generating questions (LLM calls take a few seconds)
        await interaction.response.defer(thinking=True, ephemeral=True)

        try:
            questions = await self._generate_questions(lesson, count)
        except Exception as exc:
            log.error("Failed to generate quiz questions: %s", exc, exc_info=True)
            await interaction.followup.send(
                "❌ Nie udało się wygenerować pytań. "
                "Sprawdź, czy lekcje są zaindeksowane (`ENABLE_GRAPH_RAG=true`).",
                ephemeral=True,
            )
            return

        if not questions:
            await interaction.followup.send(
                "❌ Brak dostępnych pojęć. "
                "Zaindeksuj lekcje zanim zaczniesz quiz.",
                ephemeral=True,
            )
            return

        session = _QuizSession(user_id=interaction.user.id, questions=questions)
        self.sessions[interaction.user.id] = session

        first_q = questions[0]
        embed = _build_question_embed(first_q, 1, len(questions))
        view = QuizView(self, interaction.user.id, first_q)
        await interaction.followup.send(embed=embed, view=view, ephemeral=True)

    @app_commands.command(
        name="end-quiz",
        description="Zakończ aktywną sesję quizową i wyświetl podsumowanie",
    )
    async def end_quiz(self, interaction: discord.Interaction) -> None:
        session = self.sessions.pop(interaction.user.id, None)
        if session is None:
            await interaction.response.send_message(
                "❌ Nie masz aktywnej sesji quizowej.",
                ephemeral=True,
            )
            return

        if not session.evaluations:
            await interaction.response.send_message(
                "❌ Nie udzielono jeszcze żadnej odpowiedzi — sesja zakończona bez podsumowania.",
                ephemeral=True,
            )
            return

        summary_embed = _build_summary_embed(session)
        await interaction.response.send_message(embed=summary_embed)

    # ── Internal helpers ─────────────────────────────────────────────

    async def _generate_questions(
        self,
        lesson_filter: str | None,
        count: int,
    ) -> list[_QuizQuestion]:
        """Fetch concepts, retrieve context, and generate questions (in a thread)."""
        from processor.tools.graph_rag import (
            get_all_concepts,
            get_lesson_concepts,
            retrieve,
        )
        from processor.agents.quiz_evaluator import build_context, generate_question_async

        driver = self.bot.neo4j_driver
        config = self.bot.config
        async_llm = self.bot.async_llm

        # Fetch concept list
        if lesson_filter:
            lesson_upper = lesson_filter.strip().upper()
            concepts = await asyncio.to_thread(get_lesson_concepts, driver, lesson_upper)
        else:
            concepts = await asyncio.to_thread(get_all_concepts, driver)

        if not concepts:
            return []

        selected = random.sample(concepts, min(count, len(concepts)))
        questions: list[_QuizQuestion] = []

        for topic_data in selected:
            topic = topic_data["name"]
            try:
                result = await asyncio.to_thread(
                    retrieve, driver, topic, max_results=8
                )
                context = build_context(result, topic)
                q_obj = await generate_question_async(
                    topic, context, async_llm, config.model_large
                )
                questions.append(
                    _QuizQuestion(
                        question=q_obj.text,
                        topic=topic,
                        question_type=q_obj.question_type,
                        options=q_obj.options,
                        context=context,
                        source_lessons=result.source_lessons,
                        reference_answer=q_obj.reference_answer,
                    )
                )
            except Exception as exc:
                log.warning("Skipping topic %r: %s", topic, exc)

        return questions

    async def _handle_answer(
        self,
        interaction: discord.Interaction,
        user_id: int,
        answer: str,
    ) -> None:
        """Evaluate the user's answer and advance the session."""
        from processor.agents.quiz_evaluator import evaluate_answer_async

        session = self.sessions.get(user_id)
        if session is None:
            await interaction.followup.send(
                "❌ Sesja kwizowa wygasła. Zacznij nową komendą `/quiz`.",
                ephemeral=True,
            )
            return

        q = session.questions[session.current_index]
        config = self.bot.config

        # Show a "thinking" placeholder while the LLM evaluates
        thinking = await interaction.followup.send("⏳ Oceniam odpowiedź…", ephemeral=True)

        try:
            evaluation = await evaluate_answer_async(
                question=q.question,
                reference_answer=q.reference_answer,
                user_answer=answer,
                context=q.context,
                llm=self.bot.async_llm,
                model=config.model_large,
            )
        except Exception as exc:
            log.error("Error evaluating answer: %s", exc, exc_info=True)
            await thinking.edit(
                content="❌ Błąd podczas oceniania odpowiedzi. Sesja zakończona."
            )
            self.sessions.pop(user_id, None)
            return

        session.evaluations.append(
            {
                "topic": q.topic,
                "question": q.question,
                "user_answer": answer,
                "score": evaluation.score,
                "feedback": evaluation.feedback,
            }
        )
        session.current_index += 1

        eval_embed = _build_evaluation_embed(
            score=evaluation.score,
            feedback=evaluation.feedback,
            correct_answer=evaluation.correct_answer,
            grounding_sources=evaluation.grounding_sources,
            user_answer=answer,
            question_number=session.current_index,
            total=len(session.questions),
        )
        await thinking.edit(content=None, embed=eval_embed)

        # Continue with the next question or close the session
        if session.current_index < len(session.questions):
            next_q = session.questions[session.current_index]
            q_embed = _build_question_embed(
                next_q,
                session.current_index + 1,
                len(session.questions),
            )
            view = QuizView(self, user_id, next_q)
            await interaction.followup.send(embed=q_embed, view=view, ephemeral=True)
        else:
            summary_embed = _build_summary_embed(session)
            await interaction.followup.send(embed=summary_embed, ephemeral=True)
            self.sessions.pop(user_id, None)


async def setup(bot: commands.Bot) -> None:
    await bot.add_cog(QuizCog(bot))  # type: ignore[arg-type]
