# DevScope — AI Codebase Intelligence Platform

> Ask your codebase questions in plain English. Get back relevant files, flow explanations, and call graph summaries.

```
"Where is payment retry handled?"
→ PaymentService.java:42, RetryConfig.java:18
→ "PaymentService.processPayment() calls retryPayment() with exponential backoff..."
→ Call graph: PaymentController → PaymentService → RetryHandler → PaymentRepository
```

---

## What it does

DevScope indexes a Git repository and lets engineers query it semantically. Built to solve the real problem of onboarding engineers into large Java/Spring codebases.

| Feature | Description |
|---------|-------------|
| **Semantic search** | Ask natural language questions, get back the most relevant methods and files |
| **Flow explanation** | LLM-generated explanation of how code flows through the matched area |
| **Call graph** | JavaParser extracts method-call edges; query returns a 2-hop call graph summary |
| **Explain like I'm new** | Special mode that generates onboarding-style walkthroughs |
| **Multi-language** | Java, Python, Go, TypeScript, Kotlin supported via tree-sitter |

---

## Architecture

```
POST /repos/ingest      POST /query           GET /graph/{repoId}/{class}
        │                     │                          │
        ▼                     ▼                          ▼
┌──────────────────────────────────────────────────────────┐
│                  Spring Boot 3  (port 8080)              │
├────────────────────┬─────────────────────────────────────┤
│  Ingestion         │  Query Engine                       │
│  ─────────         │  ────────────                       │
│  RepoUnpacker      │  EmbeddingService (OpenAI)          │
│  TreeSitterChunker │  VectorSearchService (pgvector)     │
│  EmbeddingService  │  LLMSynthesizer (GPT-4o)            │
│  GraphExtractor    │  QueryService                       │
└────────────────────┴──────────────┬──────────────────────┘
                                    │
                    ┌───────────────▼───────────────┐
                    │   PostgreSQL + pgvector        │
                    │   repos, code_chunks,          │
                    │   dependency_edges             │
                    └────────────────────────────────┘
```

**Ingestion pipeline:**
1. Unpack zip / clone Git URL
2. Walk source files, filter by language
3. Parse with **tree-sitter** → method-level chunks
4. Batch embed via **OpenAI text-embedding-3-small**
5. Store vectors in **pgvector** (cosine index)
6. Extract call graph with **JavaParser** → store edges

**Query pipeline:**
1. Embed question → cosine search (top-10)
2. Feed chunks + question to **GPT-4o**
3. Return: `relevant_files`, `explanation`, `call_graph_summary`

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend API | Java 21 + Spring Boot 3.2 |
| Build | Gradle (Kotlin DSL) |
| Code parsing | tree-sitter (Python subprocess) |
| Embeddings | OpenAI `text-embedding-3-small` (1536d) |
| Vector DB | PostgreSQL 16 + pgvector |
| LLM synthesis | OpenAI GPT-4o |
| Graph extraction | JavaParser 3.25 |
| DB migrations | Flyway |

---

## Prerequisites

- Java 21+
- Docker + Docker Compose
- Python 3.10+
- An OpenAI API key

---

## Getting Started

### 1. Clone the repo

```bash
git clone <this-repo>
cd codebase-copilot
```

### 2. Set your API key

```bash
export OPENAI_API_KEY=sk-...
```

### 3. Start PostgreSQL

```bash
docker-compose up -d
```

### 4. Install Python chunker dependencies

```bash
pip3 install -r scripts/requirements.txt
```

### 5. Start the backend

```bash
cd backend
./gradlew bootRun
```

The API is now live at `http://localhost:8080`.

---

## Usage

### Ingest a repository

**Option A — Zip upload:**
```bash
curl -X POST http://localhost:8080/repos/ingest/zip \
  -F "file=@my-service.zip" \
  -F "repoName=my-service"
```

**Option B — Git URL:**
```bash
curl -X POST http://localhost:8080/repos/ingest/git \
  -H 'Content-Type: application/json' \
  -d '{"repoName": "spring-petclinic", "repoUrl": "https://github.com/spring-projects/spring-petclinic"}'
```

Both return immediately with a `repoId`. Ingestion runs async.

### Check ingestion status

```bash
curl http://localhost:8080/repos/{repoId}/status
# { "status": "COMPLETED", ... }
```

### Query the codebase

```bash
curl -X POST http://localhost:8080/query \
  -H 'Content-Type: application/json' \
  -d '{
    "repoId": "<repoId>",
    "question": "Where is payment retry handled?"
  }'
```

**Response:**
```json
{
  "relevantFiles": ["src/main/java/com/example/PaymentService.java"],
  "explanation": "Payment retry is handled in PaymentService.retryPayment()...",
  "callGraphSummary": "PaymentController.checkout() → PaymentService.processPayment() → RetryHandler.retry()",
  "chunks": [
    {
      "filePath": "src/main/java/com/example/PaymentService.java",
      "className": "PaymentService",
      "methodName": "retryPayment",
      "startLine": 42,
      "endLine": 67,
      "content": "...",
      "similarity": 0.91
    }
  ]
}
```

### Explain like I'm new

```bash
curl -X POST http://localhost:8080/query \
  -H 'Content-Type: application/json' \
  -d '{
    "repoId": "<repoId>",
    "question": "How does the order flow work end to end?",
    "mode": "EXPLAIN_LIKE_NEW"
  }'
```

### Get call graph for a class

```bash
curl http://localhost:8080/graph/{repoId}/PaymentService
```

---

## API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/repos/ingest/zip` | Ingest a zip file (multipart) |
| `POST` | `/repos/ingest/git` | Ingest a public Git URL |
| `GET`  | `/repos/{repoId}/status` | Check ingestion status |
| `POST` | `/query` | Query the codebase |
| `GET`  | `/graph/{repoId}/{className}` | Get call graph edges for a class |

---

## Project Structure

```
codebase-copilot/
├── docker-compose.yml
├── scripts/
│   ├── chunker.py          # tree-sitter parser (Python)
│   ├── requirements.txt    # tree-sitter dependencies
│   └── run_local.sh        # one-shot local startup
└── backend/
    ├── build.gradle.kts
    └── src/main/java/com/devscope/
        ├── api/            # REST controllers + exception handler
        ├── config/         # Spring config (RestClient, Async)
        ├── ingestion/      # Pipeline: unpack → chunk → embed → graph
        ├── query/          # Vector search + LLM synthesis
        └── model/          # JPA entities, repositories, DTOs
```

---

## Configuration

All config lives in `backend/src/main/resources/application.yml`.
Secrets are injected via environment variables — never hardcoded.

| Env Variable | Required | Description |
|-------------|----------|-------------|
| `OPENAI_API_KEY` | Yes | OpenAI API key for embeddings + LLM |

To swap the LLM to Claude, set `devscope.llm.model` and point the base URL to the Anthropic API.

---

## Running Tests

```bash
cd backend
./gradlew test
```

Integration tests use Testcontainers (requires Docker running).
