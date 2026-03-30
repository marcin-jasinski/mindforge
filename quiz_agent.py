#!/usr/bin/env python3
"""
Quiz Agent — interactive assessment runner powered by graph-RAG.

Instead of static quiz.md files with pre-generated answers, this agent:
  1. Selects topics from the Neo4j concept graph
  2. Retrieves relevant chunks and context via graph-RAG
  3. Generates questions grounded in source material
  4. Accepts the user's answer interactively
  5. Evaluates the answer against retrieved sources
  6. Reports feedback with grounding references

Usage:
  python quiz_agent.py                         # interactive quiz (all lessons)
  python quiz_agent.py --lesson S01E01         # quiz on specific lesson
  python quiz_agent.py --count 5               # number of questions
  python quiz_agent.py --lesson S01E01 --count 3
"""
from __future__ import annotations

import argparse
import json
import logging
import random
import sys
from dataclasses import asdict
from pathlib import Path

# Ensure project root is on sys.path
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from processor.llm_client import LLMClient, load_config
from processor.tools.graph_rag import (
    GraphConfig,
    RetrievalResult,
    connect,
    ensure_indexes,
    get_all_concepts,
    get_indexed_lessons,
    get_lesson_concepts,
    retrieve,
)
from processor import tracing

# ── Re-export quiz logic from the canonical module (CRITICAL-2) ──────────────
# All quiz dataclasses and functions now live in processor.agents.quiz_evaluator.
# This file is a thin CLI wrapper only.

from processor.agents.quiz_evaluator import (
    Question,
    Evaluation,
    SessionResult,
    build_context,
    generate_question,
    evaluate_answer,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)-7s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("quiz-agent")



# ── Interactive session ─────────────────────────────────────────────


def run_quiz_session(
    driver,
    llm: LLMClient,
    model: str,
    *,
    lesson_filter: str | None = None,
    question_count: int = 5,
) -> SessionResult:
    """Run an interactive quiz session."""
    # Get available concepts
    if lesson_filter:
        concepts = get_lesson_concepts(driver, lesson_filter)
        if not concepts:
            print(f"\nBrak pojęć dla lekcji {lesson_filter}. Czy lekcja jest zaindeksowana?")
            return SessionResult(total_questions=0, answered=0, average_score=0.0, evaluations=[])
    else:
        concepts = get_all_concepts(driver)
        if not concepts:
            print("\nBrak pojęć w grafie. Najpierw zaindeksuj lekcje pipeline'em.")
            return SessionResult(total_questions=0, answered=0, average_score=0.0, evaluations=[])

    # Select random topics
    selected = random.sample(concepts, min(question_count, len(concepts)))

    print("\n" + "=" * 60)
    scope = f"lekcji {lesson_filter}" if lesson_filter else "wszystkich lekcji"
    print(f"  Quiz — {len(selected)} pytań na podstawie {scope}")
    print("=" * 60)
    print("  Wpisz odpowiedź i naciśnij Enter.")
    print("  Wpisz 'q' aby zakończyć wcześniej.\n")

    evaluations: list[dict] = []
    total_score = 0.0

    for i, topic_data in enumerate(selected, 1):
        topic = topic_data["name"]
        print(f"\n{'─' * 50}")
        print(f"  Pytanie {i}/{len(selected)} — temat: {topic}")
        print(f"{'─' * 50}\n")

        # Retrieve context from graph
        result = retrieve(driver, topic, max_results=8)
        context = _build_context(result, topic)

        # Generate question
        gen = tracing.start_generation(
            name="quiz/question",
            model=model,
            input_data={"topic": topic},
        )
        try:
            question = generate_question(topic, context, llm, model)
            question.source_lessons = result.source_lessons
            question.grounding_chunks = [c.get("id", "") for c in result.chunks]
            gen.end(output=question.text)
        except Exception as exc:
            gen.error(str(exc))
            log.error("Failed to generate question for topic '%s'", topic, exc_info=True)
            print("  [Błąd generowania pytania, pomijam]\n")
            continue

        # Display question
        print(f"  {question.text}\n")
        if question.options:
            for opt in question.options:
                print(f"    {opt}")
            print()

        # Get user answer
        try:
            user_answer = input("  Twoja odpowiedź: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n\n  Zakończono.")
            break

        if user_answer.lower() in ("q", "quit", "exit"):
            print("\n  Zakończono wcześniej.")
            break

        if not user_answer:
            print("  [Pominięto — brak odpowiedzi]\n")
            continue

        # Evaluate answer
        gen = tracing.start_generation(
            name="quiz/evaluate",
            model=model,
            input_data={"question": question.text, "answer": user_answer},
        )
        try:
            evaluation = evaluate_answer(
                question=question.text,
                reference_answer=question.reference_answer,
                user_answer=user_answer,
                context=context,
                llm=llm,
                model=model,
            )
            evaluation.grounding_sources = result.source_lessons
            gen.end(output={"score": evaluation.score, "feedback": evaluation.feedback})
        except Exception as exc:
            gen.error(str(exc))
            log.error("Failed to evaluate answer", exc_info=True)
            print("  [Błąd oceny odpowiedzi]\n")
            continue

        total_score += evaluation.score

        # Display feedback
        score_pct = int(evaluation.score * 100)
        bar = "█" * (score_pct // 10) + "░" * (10 - score_pct // 10)
        print(f"\n  Ocena: {bar} {score_pct}%")
        print(f"  {evaluation.feedback}")
        if evaluation.correct_answer:
            print(f"\n  Poprawna odpowiedź: {evaluation.correct_answer}")
        if evaluation.grounding_sources:
            print(f"  Źródła: {', '.join(evaluation.grounding_sources)}")

        # Record score in Langfuse
        tracing.score(
            name="quiz_answer_score",
            value=evaluation.score,
            comment=f"Topic: {topic}",
        )

        evaluations.append({
            "question": question.text,
            "topic": topic,
            "user_answer": user_answer,
            "score": evaluation.score,
            "feedback": evaluation.feedback,
            "correct_answer": evaluation.correct_answer,
            "source_lessons": evaluation.grounding_sources,
        })

    # Summary
    answered = len(evaluations)
    avg_score = total_score / answered if answered else 0.0

    print(f"\n{'=' * 60}")
    print(f"  Wynik końcowy: {answered} pytań, średnia ocena: {avg_score:.0%}")
    print(f"{'=' * 60}\n")

    return SessionResult(
        total_questions=len(selected),
        answered=answered,
        average_score=avg_score,
        evaluations=evaluations,
    )


# ── Entry point ─────────────────────────────────────────────────────


def main() -> None:
    parser = argparse.ArgumentParser(description="MindForge Quiz Agent")
    parser.add_argument("--lesson", type=str, help="Filter to specific lesson (e.g. S01E01)")
    parser.add_argument("--count", type=int, default=5, help="Number of questions (default: 5)")
    parser.add_argument(
        "--list-lessons", action="store_true", help="List indexed lessons and exit"
    )
    args = parser.parse_args()

    config = load_config(ROOT)

    # Load Neo4j config from env
    from dotenv import dotenv_values
    env = dotenv_values(ROOT / ".env")
    graph_cfg = GraphConfig(
        uri=str(env.get("NEO4J_URI", "bolt://localhost:7687")).strip(),
        username=str(env.get("NEO4J_USERNAME", "neo4j")).strip(),
        password=str(env.get("NEO4J_PASSWORD", "password")).strip(),
    )

    try:
        driver = connect(graph_cfg)
    except Exception:
        log.error("Cannot connect to Neo4j at %s", graph_cfg.uri, exc_info=True)
        print(f"\nNie można połączyć się z Neo4j ({graph_cfg.uri}).")
        print("Upewnij się, że Neo4j jest uruchomiony:")
        print("  docker compose --profile graph up -d")
        sys.exit(1)

    if args.list_lessons:
        lessons = get_indexed_lessons(driver)
        if not lessons:
            print("Brak zaindeksowanych lekcji.")
        else:
            print("\nZaindeksowane lekcje:")
            for lesson in lessons:
                print(f"  {lesson['number']} — {lesson['title']}")
        driver.close()
        sys.exit(0)

    with tracing.trace(
        name="quiz-session",
        input_data={"lesson": args.lesson, "count": args.count},
        tags=["quiz-agent"],
    ):
        result = run_quiz_session(
            driver,
            config.llm,
            config.model_large,
            lesson_filter=args.lesson,
            question_count=args.count,
        )

    # Save session result
    results_dir = ROOT / "state" / "quiz_results"
    results_dir.mkdir(parents=True, exist_ok=True)
    import time
    result_file = results_dir / f"session_{int(time.time())}.json"
    result_file.write_text(
        json.dumps(asdict(result), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    log.info("Session result saved: %s", result_file)

    driver.close()


if __name__ == "__main__":
    main()
