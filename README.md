# Markdown Lesson Summarizer

An intelligent AI agent system for processing and summarizing markdown lesson files. The system automatically detects and removes story/task sections, fetches relevant articles from embedded links, and generates structured summaries using large language models (LLMs).

## Features

- **Intelligent Content Cleaning**: Automatically removes video embeds, image references, and story/task sections
- **Smart Link Extraction**: Identifies and classifies relevant links from lesson content
- **Article Fetching**: Retrieves content from external links with configurable limits
- **LLM-Powered Summarization**: Generates structured summaries using OpenRouter API
- **Real-Time Monitoring**: Watch directory for new files and process them automatically
- **State Tracking**: Maintains a record of processed files to avoid duplicates
- **Cost Optimization**: Uses small model for preprocessing, large model only for final summaries
- **Markdown Output**: Generates structured Markdown with YAML frontmatter metadata

## System Architecture

```
Input (nowe/*.md)
    ↓
[Parser] → Extract frontmatter, links
    ↓
[Cleaner] → Remove videos, images
    ↓
[Preprocessor] → Detect and remove story/task sections
    ↓
[Article Fetcher] → Classify and fetch relevant links
    ↓
[Summarizer] → Generate structured summary
    ↓
[Output] → podsumowane/ (+ archiwum/ + state tracking)
```

## Prerequisites

- **Python**: 3.9+ (tested with Python 3.13)
- **OpenRouter Account**: For LLM API access
- **Internet Connection**: Required for LLM API calls and article fetching

## Installation

### 1. Clone or Download the Project

```bash
cd markdown-summarizer
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
markdown-summarizer/
├── new/              # Drop lesson .md files here
├── summarized/       # Processed summaries (output)
├── archive/          # Original input files (archived after processing)
├── state/            # Internal state tracking (processed.json)
└── processor/        # Core modules
    ├── agents/       # Summarization agents
    ├── tools/        # Utility functions
    ├── llm_client.py # LLM integration
    ├── pipeline.py   # Orchestration
    ├── watcher.py    # File monitoring
    └── state.py      # State management
```

## Usage

### Running the Summarizer

#### Default Mode: Process Existing + Watch for New Files

```bash
python markdown_summarizer.py
```

This will:
1. Process all unprocessed `.md` files in `new/` directory
2. Continue watching for new files indefinitely
3. Process new files as they appear

#### Batch Mode: Process Existing Files Only

```bash
python markdown_summarizer.py --once
```

Processes existing files in `new/` and exits (no file watching).

#### Watch Mode Only: Skip Initial Processing

```bash
python markdown_summarizer.py --watch
```

Skips processing existing files and immediately starts watching for new ones.

#### Single File Mode: Process Specific File

```bash
python markdown_summarizer.py path/to/file.md
```

or

```bash
python markdown_summarizer.py file.md  # Looks in new/ directory
```

### Workflow Example

1. **Add a lesson file**:
   ```bash
   cp path/to/lesson.md new/s01e01-my-lesson.md
   ```

2. **Run the processor**:
   ```bash
   python markdown_summarizer.py --once
   ```

3. **Check output**:
   - `summarized/s01e01-my-lesson.md` — Structured summary with frontmatter
   - `archive/s01e01-my-lesson.md` — Original file (moved here after processing)
   - `state/processed.json` — File recorded as processed

## Output Format

The generated summaries include YAML frontmatter with metadata:

```markdown
---
title: "Lesson Title"
source: s01e01-original-filename.md
processed_at: 2026-03-15T20:47:40Z
lesson_number: S01E01
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

The system executes 8 sequential steps:

| Step | Module | Operation |
|------|--------|-----------|
| 1 | State | Check if file already processed |
| 2 | Parser | Extract frontmatter and links (125+ links typical) |
| 3 | Cleaner | Remove video embeds, images (regex-based) |
| 4 | Preprocessor | Detect and remove story/task sections (regex + LLM fallback) |
| 5 | Fetcher | Classify links and fetch article content (max 3 articles) |
| 6 | Summarizer | Generate final summary using large LLM (~gpt-4o) |
| 7 | Writer | Save summary with frontmatter |
| 8 | Archive | Move original file to archive |

**Typical Processing Time**: 60-90 seconds per file (depends on LLM latency)

## Model Selection

The system uses two LLM models for cost optimization:

### Small Model (gpt-4o-mini)
- **Used for**: Link classification, story/task section detection
- **Reason**: Fast and cheap, sufficient accuracy for preprocessing
- **Cost**: ~$0.01 per file

### Large Model (gpt-4o)
- **Used for**: Final lesson summarization only
- **Reason**: Higher quality output for critical content
- **Cost**: ~$0.05 per file

**Total estimated cost**: ~$0.06 per file with OpenRouter pricing

## State Tracking

The system tracks processed files in `state/processed.json` to prevent duplicate processing:

```json
{
  "processed": [
    "s01e01-lesson-one.md",
    "s01e02-lesson-two.md"
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
# Edit logging level in markdown_summarizer.py
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
3. Ensure `.env` is in the same directory as `markdown_summarizer.py`

### Issue: Long processing time (>2 minutes per file)
**Solution**: Check internet connection and OpenRouter API status. Normal processing time is 60-90 seconds.

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
# 3. Run: python markdown_summarizer.py --once

# Monitor in real-time (watch mode)
python markdown_summarizer.py  # Ctrl+C to stop

# Clean test
rm -rf summarized/* archive/* state/processed.json
python markdown_summarizer.py --once
```

## Performance Considerations

- **File Size**: Handles 50-100KB markdown files efficiently (4th-devs course lessons)
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
markdown-summarizer/
├── markdown_summarizer.py     # Entry point (main script)
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
