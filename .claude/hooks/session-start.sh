#!/bin/bash
# Cloud sessions only: start the Docker daemon (Testcontainers needs it), fill the
# Maven cache and build the frontend once, so the factory checks
# (`mvn -o test`, `mvn verify -DskipFrontend=true`, the frontend gate) run.
set -euo pipefail
exec 1>&2   # stdout of a SessionStart hook goes into the session context; keep it clean

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-.}"

# Docker daemon: installed in the cloud image, but not started.
if ! docker info >/dev/null 2>&1; then
  if ! command -v dockerd >/dev/null; then
    echo "session-start: dockerd is not installed; integration tests cannot run" >&2
    exit 1
  fi
  nohup dockerd >/tmp/dockerd.log 2>&1 &
  for _ in $(seq 1 60); do
    docker info >/dev/null 2>&1 && break
    sleep 1
  done
  if ! docker info >/dev/null 2>&1; then
    echo "session-start: dockerd did not start; see /tmp/dockerd.log" >&2
    exit 1
  fi
fi

# Maven cache: resolve all plugins and dependencies once per pom.xml change,
# so the offline fast check works.
m2="${HOME}/.m2/repository"
hashes="${m2}/.session-start.sha256"
if ! sha256sum --check --status "$hashes" 2>/dev/null; then
  rm -f "$hashes"
  mvn -B -q dependency:go-offline -DskipFrontend=true
  # go-offline does not fetch the Surefire test provider, and Surefire fetches
  # it only when a test runs: run one small unit test.
  mvn -B -q test -DskipFrontend=true -Djacoco.skip=true -Dtest=PlainTextParserTest
  mkdir -p "$m2"
  sha256sum .claude/hooks/session-start.sh pom.xml > "$hashes" || rm -f "$hashes"
fi

# Frontend: node_modules for the frontend gate, and a dist/ build, because
# `mvn verify -DskipFrontend=true` still copies frontend/dist into the JAR and an
# integration test expects the single-page app there.
fe_hashes=frontend/node_modules/.session-start.sha256
if ! sha256sum --check --status "$fe_hashes" 2>/dev/null; then
  rm -f "$fe_hashes"
  (cd frontend && npm install --no-audit --no-fund)
  sha256sum .claude/hooks/session-start.sh frontend/package.json frontend/package-lock.json > "$fe_hashes" || rm -f "$fe_hashes"
fi
if [ ! -f frontend/dist/frontend/browser/index.html ]; then
  (cd frontend && npm run build)
fi
