"""
Tests for P0.5 — Discord interactive-view ownership checks.

QuizView (MC button callback):
  ✓ Owner can click a multiple-choice button
  ✓ Non-owner click is rejected with an ephemeral denial

QuizView (open-answer button callback):
  ✓ Owner opens the answer modal
  ✓ Non-owner click is rejected with an ephemeral denial

AnswerModal.on_submit:
  ✓ Owner submission is forwarded to _handle_answer
  ✓ Non-owner submission is rejected with an ephemeral denial

SearchView pagination:
  ✓ Owner can navigate to next page
  ✓ Owner can navigate to previous page
  ✓ Non-owner next click is rejected with an ephemeral denial
  ✓ Non-owner prev click is rejected with an ephemeral denial
  ✓ SearchView stores user_id from constructor
"""
from __future__ import annotations

import sys
from pathlib import Path
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import discord

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_interaction(user_id: int = 1, response_done: bool = False) -> MagicMock:
    interaction = MagicMock()
    user = MagicMock(spec=discord.User)
    user.id = user_id
    interaction.user = user
    interaction.guild = None

    response = MagicMock()
    response.is_done.return_value = response_done
    response.send_message = AsyncMock()
    response.send_modal = AsyncMock()
    response.edit_message = AsyncMock()
    response.defer = AsyncMock()
    interaction.response = response

    interaction.followup = MagicMock()
    interaction.followup.send = AsyncMock()
    return interaction


def _make_search_result(num_chunks: int = 5) -> MagicMock:
    result = MagicMock()
    result.chunks = [{"lesson_number": f"S01E0{i}", "text": f"chunk {i}"} for i in range(num_chunks)]
    result.concepts = []
    result.facts = []
    result.source_lessons = []
    return result


# ---------------------------------------------------------------------------
# SearchView ownership tests
# ---------------------------------------------------------------------------

class TestSearchViewOwnership:
    def setup_method(self):
        # Import after sys.path is set
        from discord_bot.cogs.search import SearchView
        self.SearchView = SearchView

    def test_stores_user_id(self):
        result = _make_search_result(3)
        view = self.SearchView(result, "test query", user_id=42)
        assert view.user_id == 42

    @pytest.mark.asyncio
    async def test_owner_can_navigate_next(self):
        result = _make_search_result(10)
        view = self.SearchView(result, "test", user_id=42)
        interaction = _make_interaction(user_id=42)
        await view._next_callback(interaction)
        interaction.response.send_message.assert_not_awaited()
        assert view.page == 1

    @pytest.mark.asyncio
    async def test_owner_can_navigate_prev(self):
        result = _make_search_result(10)
        view = self.SearchView(result, "test", user_id=42)
        view.page = 2
        interaction = _make_interaction(user_id=42)
        await view._prev_callback(interaction)
        interaction.response.send_message.assert_not_awaited()
        assert view.page == 1

    @pytest.mark.asyncio
    async def test_non_owner_next_denied(self):
        result = _make_search_result(10)
        view = self.SearchView(result, "test", user_id=42)
        interaction = _make_interaction(user_id=999)
        await view._next_callback(interaction)
        interaction.response.send_message.assert_awaited_once()
        call_kwargs = interaction.response.send_message.call_args
        assert call_kwargs.kwargs.get("ephemeral") is True
        assert view.page == 0  # page must not advance

    @pytest.mark.asyncio
    async def test_non_owner_prev_denied(self):
        result = _make_search_result(10)
        view = self.SearchView(result, "test", user_id=42)
        view.page = 2
        interaction = _make_interaction(user_id=999)
        await view._prev_callback(interaction)
        interaction.response.send_message.assert_awaited_once()
        call_kwargs = interaction.response.send_message.call_args
        assert call_kwargs.kwargs.get("ephemeral") is True
        assert view.page == 2  # page must not change


# ---------------------------------------------------------------------------
# QuizView button ownership tests
# ---------------------------------------------------------------------------

class TestQuizViewOwnership:
    def setup_method(self):
        from discord_bot.cogs.quiz import QuizView, _QuizQuestion
        self.QuizView = QuizView
        self._QuizQuestion = _QuizQuestion

    def _make_question(self, q_type: str = "multiple_choice") -> object:
        return self._QuizQuestion(
            question="What is X?",
            topic="X",
            question_type=q_type,
            options=["A", "B"] if q_type in ("multiple_choice", "true_false") else None,
            context="context",
            source_lessons=[],
        )

    def _make_cog(self) -> MagicMock:
        cog = MagicMock()
        cog._handle_answer = AsyncMock()
        return cog

    @pytest.mark.asyncio
    async def test_owner_mc_button_calls_handle_answer(self):
        cog = self._make_cog()
        question = self._make_question("multiple_choice")
        view = self.QuizView(cog, user_id=42, question=question)
        interaction = _make_interaction(user_id=42)
        # Trigger the first button's callback (option "A")
        callback = view._make_option_callback("A")
        await callback(interaction)
        cog._handle_answer.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_non_owner_mc_button_denied(self):
        cog = self._make_cog()
        question = self._make_question("multiple_choice")
        view = self.QuizView(cog, user_id=42, question=question)
        interaction = _make_interaction(user_id=999)
        callback = view._make_option_callback("A")
        await callback(interaction)
        cog._handle_answer.assert_not_awaited()
        interaction.response.send_message.assert_awaited_once()
        assert interaction.response.send_message.call_args.kwargs.get("ephemeral") is True

    @pytest.mark.asyncio
    async def test_owner_open_answer_sends_modal(self):
        cog = self._make_cog()
        question = self._make_question("open")
        view = self.QuizView(cog, user_id=42, question=question)
        interaction = _make_interaction(user_id=42)
        await view._open_answer_callback(interaction)
        interaction.response.send_modal.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_non_owner_open_answer_denied(self):
        cog = self._make_cog()
        question = self._make_question("open")
        view = self.QuizView(cog, user_id=42, question=question)
        interaction = _make_interaction(user_id=999)
        await view._open_answer_callback(interaction)
        interaction.response.send_modal.assert_not_awaited()
        interaction.response.send_message.assert_awaited_once()
        assert interaction.response.send_message.call_args.kwargs.get("ephemeral") is True


# ---------------------------------------------------------------------------
# AnswerModal ownership tests
# ---------------------------------------------------------------------------

class TestAnswerModalOwnership:
    def setup_method(self):
        from discord_bot.cogs.quiz import AnswerModal
        self.AnswerModal = AnswerModal

    def _make_cog(self) -> MagicMock:
        cog = MagicMock()
        cog._handle_answer = AsyncMock()
        return cog

    @pytest.mark.asyncio
    async def test_owner_submission_forwarded(self):
        cog = self._make_cog()
        modal = self.AnswerModal(cog, user_id=42)
        # Simulate user input via the TextInput
        modal.answer_input = MagicMock()
        modal.answer_input.value = "My answer"
        interaction = _make_interaction(user_id=42)
        await modal.on_submit(interaction)
        cog._handle_answer.assert_awaited_once()

    @pytest.mark.asyncio
    async def test_non_owner_submission_denied(self):
        cog = self._make_cog()
        modal = self.AnswerModal(cog, user_id=42)
        modal.answer_input = MagicMock()
        modal.answer_input.value = "Their answer"
        interaction = _make_interaction(user_id=999)
        await modal.on_submit(interaction)
        cog._handle_answer.assert_not_awaited()
        interaction.response.send_message.assert_awaited_once()
        assert interaction.response.send_message.call_args.kwargs.get("ephemeral") is True
