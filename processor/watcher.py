"""
File watcher — monitors nowe/ directory for new .md files.
Uses watchdog to detect file creation events.
"""
from __future__ import annotations

import logging
import time
from pathlib import Path

from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler, FileCreatedEvent

from processor.llm_client import Config
from processor import pipeline

log = logging.getLogger(__name__)

DEBOUNCE_SECONDS = 2.0


class LessonFileHandler(FileSystemEventHandler):
    """Handles new .md files appearing in the watched directory."""

    def __init__(self, config: Config) -> None:
        super().__init__()
        self.config = config
        self._last_event: dict[str, float] = {}

    def on_created(self, event: FileCreatedEvent) -> None:
        if event.is_directory:
            return

        filepath = Path(event.src_path)
        if filepath.suffix.lower() != ".md":
            return

        # Debounce: ignore duplicate events within DEBOUNCE_SECONDS
        now = time.time()
        last = self._last_event.get(filepath.name, 0)
        if now - last < DEBOUNCE_SECONDS:
            return
        self._last_event[filepath.name] = now

        log.info("New lesson file detected: %s", filepath.name)

        # Small delay to ensure file is fully written
        time.sleep(1.0)

        pipeline.run(filepath, self.config)


def start_watcher(config: Config, blocking: bool = True) -> Observer:
    """Start watching nowe/ directory for new .md files.
    
    If blocking=True, blocks until interrupted.
    If blocking=False, returns the observer (caller is responsible for stopping).
    """
    watch_dir = str(config.nowe_dir)
    log.info("Starting file watcher on: %s", watch_dir)

    handler = LessonFileHandler(config)
    observer = Observer()
    observer.schedule(handler, watch_dir, recursive=False)
    observer.start()

    if not blocking:
        return observer

    try:
        log.info("Watcher running. Press Ctrl+C to stop.")
        while observer.is_alive():
            observer.join(timeout=1)
    except KeyboardInterrupt:
        log.info("Watcher stopped by user")
        observer.stop()
    observer.join()
    return observer
