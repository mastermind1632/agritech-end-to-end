# AgriTech LangChain foundations

This is the Python LangChain service used by AgriTech, not LangChain4j. It uses the current `create_agent` API and `ChatOllama` from `langchain_ollama`.

`Frontend -> authenticated Spring /api/ai/chat -> PostgreSQL retrieval -> Python LangChain agent -> Ollama/Qwen`

Spring retrieves public product prices, open group-order terms and curated farming/app guides, supplies the current farmer's ledger summary, rejects questions with no matching source, and returns source titles. Python receives that grounded prompt and separate system instructions. It has no database credentials.

## Run

From the repository root, with the existing database and Ollama running:

```powershell
docker compose -f docker-compose.yml -f docker-compose.ai.yml up -d --build
```

For Python development (Python 3.12 recommended):

```powershell
cd langchain-service
python -m venv .venv
.venv\Scripts\python -m pip install -r requirements.txt
.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Defaults: `OLLAMA_BASE_URL=http://localhost:11434`, `AI_MODEL=qwen2.5:0.5b`. Spring uses `AI_LANGCHAIN_URL=http://localhost:8001` outside Docker. Compose supplies internal service URLs. Keep both services on the same model tag.

`GET /health` checks Ollama connectivity and identifies the framework. Internal `POST /api/generate` retains the existing non-streaming request/response shape, preserving Spring's retries and missing-model recovery. Model and generation limits are validated. The Docker overlay publishes no host ports for LangChain or Ollama; browsers call Spring through the existing authenticated `/api` proxy.

## Scope

This foundation step uses `create_agent(model=ChatOllama(...), tools=[], system_prompt=...)`. Autonomous tools are not enabled on the installed small model. Tool calling requires a supporting model and permission-scoped, tested tools. Do not use Python `eval` as a calculator.

The conversation graph has durable, JWT-owned SQLite memory and approval checkpoints.
Legacy generation requests remain independent. LangSmith graph spans are opt-in and payload-redacted;
no API key is needed unless you enable uploads. See [weekly workflow](../docs/weekly-ai-workflow.md)
for setup, privacy constraints and evaluation commands.

Tests exercise the real LangChain agent with a local fake model, without external requests or model downloads:

```powershell
python -m unittest discover -s tests -v
```

Official references: [LangChain quickstart](https://docs.langchain.com/oss/python/langchain/quickstart), [ChatOllama integration](https://docs.langchain.com/oss/python/integrations/chat/ollama).
