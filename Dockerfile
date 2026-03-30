# Stage 1: Build Angular SPA
FROM node:22-slim AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npx ng build --configuration production

# Stage 2: Python application
FROM python:3.13-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Copy Angular build output from stage 1
COPY --from=frontend-build /frontend/dist/frontend/browser/ /app/frontend/dist/frontend/browser/

# Ensure output directories exist
RUN mkdir -p new summarized archive flashcards quizzes diagrams knowledge state/artifacts

ENTRYPOINT ["python", "mindforge.py"]
CMD ["--once"]