#!/bin/sh
# Create Langfuse buckets in MinIO.
# Runs as a one-shot init container.
set -e

echo "Waiting for MinIO to be ready..."
until mc alias set myminio http://langfuse-minio:9000 minioadmin minioadmin 2>/dev/null; do
  sleep 2
done

echo "Creating buckets..."
mc mb --ignore-existing myminio/langfuse
mc mb --ignore-existing myminio/langfuse-media

echo "MinIO buckets initialized."
