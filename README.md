# MindForge

A comprehensive AI-powered tutoring and learning platform that extracts knowledge from markdown, PDF, and other documents, automatically generates study materials (flashcards, concept maps, summaries), and provides an interactive learning interface powered by LLM agents and graph-based knowledge retrieval.

## Features

- **Intelligent Content Cleaning**: Automatically removes video embeds, image references, and story/task sections
- **Smart Link Extraction**: Identifies and classifies relevant links from lesson content
- **Article Fetching**: Retrieves content from external links with local cache (7-day TTL)
- **LLM-Powered Summarization**: Generates structured summaries using OpenRouter API
- **Canonical Artifact**: Single JSON source of truth per lesson — all outputs (markdown, Anki TSV, Mermaid diagrams) are rendered from it
- **Structured Output**: Summarizer, flashcard generator, and concept mapper use JSON schema for deterministic structured output
- **Flashcard Generation**: Anki-ready flashcards (basic, cloze, reverse) exported as tab-separated CSV
- **Concept Maps**: Mermaid diagrams of concept relationships
- **Knowledge Index**: Cumulative glossary and cross-reference tracking across lessons with concept normalization (canonical names, aliases, confidence scores, source provenance, merge rules)
- **Quality Validation**: Deterministic checks for concept consistency, flashcard quality, summary completeness, concept map integrity, and cross-reference alignment
- **Eval Framework**: Automated quality scoring (concept coverage, content grounding, flashcard balance, map connectivity) with optional Langfuse reporting
- **Graph-RAG**: Neo4j-backed concept graph with lexical and embedding fallback retrieval
- **Quiz Agent**: Interactive assessment runner powered by graph-RAG — replaces static quiz files
- **Langfuse Telemetry**: Optional LLM usage, cost, and pipeline tracing via Langfuse
- **Real-Time Monitoring**: Watch directory for new files and process them automatically
- **State Tracking**: Maintains a record of processed files to avoid duplicates
- **Docker Stack**: Full local infrastructure (Langfuse, Neo4j) via Docker Compose
- **Cost Optimization**: Uses small model for preprocessing, large model only for final summaries

## System Architecture

```
Input (new/*.md)
    ↓
[Parser] → Extract frontmatter, links, images
    ↓
[Image Analyzer] → Describe diagrams/schemas (vision model)
    ↓
[Cleaner] → Remove videos, images; inject image descriptions
    ↓
[Preprocessor] → Detect and remove story/task sections
    ↓
[Article Fetcher] → Classify links, fetch articles (with cache)
    ↓
[Summarizer] → Structured JSON → SummaryData
    ↓
[Flashcard Generator] → Structured JSON → FlashcardData[]
[Concept Mapper] → Structured JSON → ConceptMapData
    ↓
[LessonArtifact] → Canonical JSON (state/artifacts/)
    ↓
[Renderers] → Markdown, Anki TSV, Mermaid diagram
    ↓
[Graph Indexer] → Neo4j (concepts, chunks, facts, relationships)
    ↓
[Output] → summarized/ + flashcards/ + diagrams/ + knowledge/

--- Quiz Agent (separate runner) ---
[Neo4j Graph] → Retrieve context → Generate question → Accept answer → Evaluate
```

## Prerequisites

- **Python**: 3.9+ (tested with Python 3.13)
- **OpenRouter Account**: For LLM API access
- **Internet Connection**: Required for LLM API calls and article fetching

## Installation

### 1. Clone or Download the Project

```bash
cd mindforge
```

### 2. Create a Python Virtual Environment

```bash
# Windows
python -m venv venv
venv\Scripts\activate

# macOS/Linux
python3 -m venv venv
source venv/bin/activate
```

### 3. Install Dependencies

```bash
pip install -r requirements.txt
```

**Required packages:**
- `watchdog>=4.0.0` — File system monitoring
- `python-frontmatter>=1.1.0` — YAML frontmatter parsing
- `requests>=2.31.0` — HTTP requests for article fetching
- `python-dotenv>=1.0.0` — Environment variable management
- `langfuse>=2.0.0` — LLM observability (optional, tracing disabled by default)
- `neo4j>=5.0.0` — Neo4j driver for graph-RAG (optional, graph disabled by default)

## Configuration

### 1. Get an OpenRouter API Key

1. Visit [OpenRouter.ai](https://openrouter.ai)
2. Sign up or log in
3. Navigate to **Settings → Keys**
4. Create a new API key

### 2. Set Up Environment Variables

Create a `.env` file in the project root (copy from `env.example`):

```bash
cp env.example .env
```

Edit `.env` and fill in your OpenRouter API key:

```env
AI_PROVIDER=openrouter
OPENROUTER_API_KEY=sk-or-v1-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1

# Small model for preprocessing (efficient)
MODEL_SMALL=openai/gpt-4o-mini

# Large model for summarization (high quality)
MODEL_LARGE=openai/gpt-4o
```

**Important**: Never commit `.env` to version control. The project includes `.env` in `.gitignore` for security.

### 3. Directory Structure

The system automatically creates the following directories:

```
mindforge/
├── new/              # Drop lesson .md files here
├── summarized/       # Processed summaries (output)
├── archive/          # Original input files (archived after processing)
├── flashcards/       # Anki-ready tab-separated flashcard files
├── diagrams/         # Mermaid concept map diagrams
├── quizzes/          # Self-assessment quizzes (legacy)
├── knowledge/        # Cumulative glossary + cross-references
├── state/            # Internal state tracking
│   ├── processed.json       # List of processed filenames
│   ├── knowledge_index.json # Cumulative concept index
│   ├── article_cache.json   # Link classification + article cache
│   └── artifacts/           # Canonical JSON per lesson (source of truth)
├── docker/           # Docker init scripts, env templates
└── processor/        # Core modules
    ├── models.py     # Canonical LessonArtifact + data models
    ├── renderers.py  # Markdown/TSV/Mermaid rendering from artifact
    ├── tracing.py    # Langfuse telemetry integration
    ├── validation.py # Deterministic quality checks
    ├── evals.py      # Eval framework with Langfuse scoring
    ├── llm_client.py # LLM integration + Config
    ├── pipeline.py   # 16-step orchestration
    ├── watcher.py    # File monitoring
    ├── state.py      # State management
    ├── agents/       # LLM-powered agents
    │   ├── summarizer.py         # Structured summary → SummaryData
    │   ├── flashcard_generator.py # Structured flashcards → FlashcardData
    │   ├── concept_mapper.py     # Structured concept map → ConceptMapData
    │   ├── quiz_generator.py     # Legacy quiz (markdown)
    │   ├── preprocessor.py       # Story/task section removal
    │   └── image_analyzer.py     # Vision model image analysis
    └── tools/        # Utility functions
        ├── lesson_parser.py    # Frontmatter + link extraction
        ├── file_ops.py         # File I/O + artifact JSON write
        ├── article_fetcher.py  # Link classification + fetch (cached)
        ├── knowledge_index.py  # Cumulative concept tracking + normalization
        ├── concept_normalizer.py # Canonical names, dedup, merge rules
        └── anki_exporter.py    # Legacy Anki CSV export
```

## Usage

### Running the Summarizer

#### Default Mode: Process Existing + Watch for New Files

```bash
python mindforge.py
```

This will:
1. Process all unprocessed `.md` files in `new/` directory
2. Continue watching for new files indefinitely
3. Process new files as they appear

#### Batch Mode: Process Existing Files Only

```bash
python mindforge.py --once
```

Processes existing files in `new/` and exits (no file watching).

#### Watch Mode Only: Skip Initial Processing

```bash
python markdown_summarizer.py --watch
```

Skips processing existing files and immediately starts watching for new ones.

#### Single File Mode: Process Specific File

```bash
python mindforge.py path/to/file.md
```

or

```bash
python mindforge.py file.md  # Looks in new/ directory
```

### Workflow Example

1. **Add a lesson file**:
   ```bash
   cp path/to/lesson.md new/01-my-lesson.md
   ```

2. **Run the processor**:
   ```bash
   python mindforge.py --once
   ```

3. **Check output**:
   - `summarized/01-my-lesson.md` — Structured summary with frontmatter
   - `archive/01-my-lesson.md` — Original file (moved here after processing)
   - `state/processed.json` — File recorded as processed

## Output Format

The generated summaries include YAML frontmatter with metadata:

```markdown
---
title: "Lesson Title"
source: 01-original-filename.md
processed_at: 2026-03-15T20:47:40Z
lesson_number: 01
topics: ["Topic 1", "Topic 2", "Topic 3"]
original_published_at: 2026-03-09T04:00:00Z
---

## Podsumowanie
(Executive summary of the lesson)

## Kluczowe koncepcje
(Key concepts explained)

## Najważniejsze informacje
(Essential facts and points)

## Praktyczne wskazówki
(Practical tips and recommendations)

## Ważne linki
(Relevant external resources)
```

**Output Language**: Polish (configurable in system prompts)

## Processing Pipeline

The system executes 16 sequential steps:

| Step | Module | Operation |
|------|--------|-----------|
| 1 | State | Check if file already processed |
| 2 | Parser | Extract frontmatter and links |
| 3 | Parser | Extract image URLs |
| 4 | Image Analyzer | Analyze images/diagrams with vision model |
| 5 | Cleaner | Remove video embeds, images; inject descriptions |
| 6 | Models | Create canonical LessonArtifact |
| 7 | Preprocessor | Detect and remove story/task sections |
| 8 | Fetcher | Classify links and fetch articles (cached) |
| 9 | Summarizer | Generate structured SummaryData (JSON schema) |
| 10 | Flashcards | Generate FlashcardData (JSON schema) |
| 11 | — | (removed — quiz replaced by quiz-agent runner) |
| 12 | Concept Map | Generate ConceptMapData (JSON schema) |
| 13 | Writer | Save artifact JSON + render all outputs |
| 14 | Validation | Deterministic quality checks + eval scoring |
| 15 | Knowledge | Update cumulative concept index (with normalization) |
| 15b | Graph | Index into Neo4j: concepts, chunks, facts, relationships |
| 16 | Archive | Move original file, mark processed |

## Model Selection

The system uses two LLM models for cost optimization:

### Small Model (gpt-4o-mini)
- **Used for**: Link classification, story/task section detection
- **Reason**: Fast and cheap, sufficient accuracy for preprocessing

### Large Model (gpt-4o)
- **Used for**: Final lesson summarization only
- **Reason**: Higher quality output for critical content

## State Tracking

The system tracks processed files in `state/processed.json` to prevent duplicate processing:

```json
{
  "processed": [
    "01-lesson-one.md",
    "02-lesson-two.md"
  ]
}
```

To reset processing state:
```bash
# Clear the list (restart from beginning)
echo '{"processed": []}' > state/processed.json
```

## Logging

All operations are logged with timestamps and severity levels:

```
21:46:35 [INFO   ] processor.pipeline: Step 2/8 — Parsed: title=...
21:46:35 [INFO   ] processor.agents.preprocessor: Regex found story/task section
21:47:31 [WARNING] processor.tools.article_fetcher: Failed to fetch article
```

**Log Levels**:
- `INFO` — Regular operations
- `WARNING` — Non-critical failures (e.g., article fetch failed, but processing continues)
- `ERROR` — Critical failures that stop processing
- `DEBUG` — (not shown by default)

Enable debug logging:
```bash
# Edit logging level in mindforge.py
logging.basicConfig(level=logging.DEBUG, ...)
```

## Troubleshooting

### Issue: "API rate limit exceeded"
**Solution**: Wait a few minutes before retrying. The system logs failures but continues with smaller content sizes on retry.

### Issue: "Failed to fetch article: 403 Forbidden"
**Solution**: Some websites block automated requests. The system logs this as a warning and continues with remaining articles.

### Issue: "File already processed but not in summary"
**Solution**: Check `state/processed.json`. If you want to reprocess a file:
```bash
# Edit state/processed.json and remove the filename from the list
# Or reset entirely and reprocess all files
```

### Issue: "Module not found: processor.xxx"
**Solution**: Ensure you're running from the project root directory:
```bash
cd markdown-summarizer
python markdown_summarizer.py
```

### Issue: "OPENROUTER_API_KEY not found"
**Solution**: 
1. Check that `.env` exists in the project root
2. Verify the API key is not empty in `.env`
3. Ensure `.env` is in the same directory as `mindforge.py`

### Issue: Long processing time (>2 minutes per file)
**Solution**: Check internet connection and OpenRouter API status. Normal processing time is 60-90 seconds.

## Docker Stack

The project includes a full local infrastructure via Docker Compose:

### Quick Start

```bash
docker compose up --build
```

This starts:
- **mindforge-app** — the pipeline application
- **langfuse-web** + **langfuse-worker** — LLM observability UI/API
- **langfuse-postgres** — Langfuse OLTP database
- **langfuse-clickhouse** — Langfuse OLAP store
- **langfuse-redis** — Langfuse cache/queue
- **langfuse-minio** + **langfuse-minio-init** — S3-compatible storage for Langfuse
- **neo4j** — Graph database for concept graph-RAG and quiz-agent

### Profiles

Run subsets of the stack:

```bash
docker compose --profile app up                         # App only
docker compose --profile observability up                # Langfuse stack only
docker compose --profile graph up                        # Neo4j only
docker compose --profile quiz up                         # Quiz agent + Neo4j
docker compose --profile app --profile graph up          # App + Neo4j
docker compose up                                        # Full stack
```

### Backfill (populate graph from archive)

Re-process archived lessons to regenerate all artifacts and feed the Neo4j graph:

```bash
# Start Neo4j first
docker compose --profile graph up -d

# Set ENABLE_GRAPH_RAG=true in .env, then:
python backfill.py                        # backfill all archived lessons
python backfill.py --lesson S01E01        # backfill a single lesson
python backfill.py --dry-run              # preview without processing
python backfill.py --force-graph          # enable graph indexing without editing .env
python backfill.py --reset-index          # clear knowledge index before rebuilding
python backfill.py --graph-only           # re-index into Neo4j from existing artifact JSONs
```

> **Note:** `--graph-only` requires artifact JSONs to already exist in `state/artifacts/`.
> Run without `--graph-only` first to generate them.

### Quiz Agent

The quiz agent runs as a separate interactive process:

```bash
# Local (requires Neo4j running and populated via backfill)
python quiz_agent.py                     # all lessons, 5 questions
python quiz_agent.py --lesson S01E01     # specific lesson
python quiz_agent.py --count 3           # fewer questions
python quiz_agent.py --list-lessons      # show indexed lessons

# Via Docker
docker compose --profile quiz run --rm quiz-agent
docker compose --profile quiz run --rm quiz-agent --lesson S01E01
```

### Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Langfuse UI | http://localhost:3100 | (set via env) |
| Neo4j Browser | http://localhost:7474 | neo4j / password |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin |

### Volumes

Named volumes persist data across restarts:
- `langfuse-postgres-data`, `langfuse-clickhouse-data`, `langfuse-minio-data`
- `neo4j-data`, `neo4j-logs`

### Reset

```bash
docker compose down -v  # Remove containers AND volumes (full reset)
```

## Advanced Configuration

### Customize LLM Models

Edit `.env` to use different models:

```env
# Use Claude instead of GPT
MODEL_SMALL=anthropic/claude-3-haiku
MODEL_LARGE=anthropic/claude-3-opus

# Use Mistral for budget option
MODEL_SMALL=mistralai/mistral-7b
MODEL_LARGE=mistralai/mistral-large
```

See [OpenRouter Models](https://openrouter.ai/docs) for available options.

### Customize Processing Behavior

Edit `processor/pipeline.py` to modify the processing pipeline or add new steps.

### Extract Specific Topics

Edit `processor/agents/summarizer.py` to change which sections are included in output.

## Common Commands

```bash
# Activate virtual environment
source venv/bin/activate  # macOS/Linux
# or
venv\Scripts\activate     # Windows

# Check Python version
python --version

# Run in debug mode (verbose logging)
python markdown_summarizer.py --once 2>&1 | tee debug.log

# Check how many files are queued
ls -la new/

# Check processed files
cat state/processed.json | python -m json.tool

# Force reprocess a specific file
# 1. Remove from state/processed.json
# 2. Move from archive/ back to new/
# 3. Run: python mindforge.py --once

# Monitor in real-time (watch mode)
python mindforge.py  # Ctrl+C to stop

# Clean test
rm -rf summarized/* archive/* state/processed.json
python mindforge.py --once
```

## Performance Considerations

- **File Size**: Handles 50-100KB markdown files efficiently
- **Batch Processing**: Can process multiple files sequentially; each completes independently
- **Memory Usage**: ~200-300MB during processing
- **Network**: Requires stable internet; retries automatically on temporary failures

## Environment Variables Reference

```env
AI_PROVIDER                    # Always "openrouter" for this tool
OPENROUTER_API_KEY            # Your API key (required)
OPENROUTER_BASE_URL           # API endpoint (https://openrouter.ai/api/v1)
MODEL_SMALL                   # Small model for preprocessing
MODEL_LARGE                   # Large model for summarization
```

## Project Structure

```
mindforge/
├── mindforge.py     # Entry point (main script)
├── requirements.txt           # Python dependencies
├── env.example                # Example environment variables
├── .env                        # Your API credentials (NOT in git)
├── .gitignore                 # Git exclusions
├── LICENSE                    # License file
├── README.md                  # This file
│
├── processor/                 # Core processing modules
│   ├── __init__.py
│   ├── llm_client.py          # LLM interface and configuration
│   ├── pipeline.py            # Main orchestration pipeline
│   ├── state.py               # Processed file tracking
│   ├── watcher.py             # File system monitoring
│   │
│   ├── agents/                # High-level processing agents
│   │   ├── __init__.py
│   │   ├── preprocessor.py    # Story/task section detection
│   │   └── summarizer.py      # Summary generation
│   │
│   └── tools/                 # Low-level utility functions
│       ├── __init__.py
│       ├── lesson_parser.py   # Markdown parsing
│       ├── file_ops.py        # File I/O
│       └── article_fetcher.py # Link classification & fetching
│
├── new/                       # Input directory (drop .md files here)
├── summarized/                # Output directory (processed summaries)
├── archive/                   # Archived originals (after processing)
└── state/                     # State tracking
    └── processed.json         # List of processed files
```

## Contributing & Modification

The codebase is modular and designed for extension:

- **Add new processing steps**: Modify `processor/pipeline.py`
- **Change output format**: Edit `processor/agents/summarizer.py`
- **Customize cleaning logic**: Update `processor/tools/lesson_parser.py`
- **Support new LLM providers**: Extend `processor/llm_client.py`

## Support & Debugging

1. **Check logs** — All operations are logged with timestamps
2. **Verify configuration** — Ensure `.env` has valid API key
3. **Test connectivity** — Verify internet connection to OpenRouter
4. **Review sample output** — Check `summarized/` directory for results
5. **Check state** — Inspect `state/processed.json` for tracking info

## License

See [LICENSE](LICENSE) file for details.

---

**Questions?** Check the logs or review the comments in individual module files for implementation details.
