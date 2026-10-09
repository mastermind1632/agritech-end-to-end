"""Internal AI gateway: authenticated conversations and a compatible stateless adapter."""
import os
from pathlib import Path
from threading import RLock
from uuid import UUID

# Never allow implicit LangChain tracing to export farmer prompts or credentials.
os.environ["LANGSMITH_TRACING"] = "false"
os.environ["LANGGRAPH_STRICT_MSGPACK"] = "true"

from fastapi import FastAPI, Header, HTTPException
from langchain_ollama import ChatOllama
from ollama import Client, ResponseError
from pydantic import BaseModel, Field, StrictBool, model_validator
from app.workflow import Backend, Workflow

MODEL = os.environ.get("AI_MODEL", "qwen2.5:0.5b")
OLLAMA_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
app = FastAPI(title="AgriTech LangGraph Service", version="2.0.0")
_engine = None
_engine_lock = RLock()


class GenerationOptions(BaseModel):
    temperature: float = Field(default=0.1, ge=0, le=1)
    num_predict: int = Field(default=250, ge=1, le=500)


class GenerateRequest(BaseModel):
    model: str
    prompt: str = Field(min_length=1, max_length=24000)
    system: str = Field(min_length=1, max_length=4000)
    stream: bool = False
    options: GenerationOptions = Field(default_factory=GenerationOptions)


class GenerateResponse(BaseModel):
    model: str
    response: str


class ReviewAction(BaseModel):
    orderId: UUID
    quantity: int = Field(default=1, ge=1, le=1000000)


class TurnRequest(BaseModel):
    prompt: str = Field(default="", max_length=2000)
    action: ReviewAction | None = None

    @model_validator(mode="after")
    def requires_question_or_action(self):
        if not self.prompt.strip() and self.action is None:
            raise ValueError("Ask a question or propose a group-order review")
        return self


class ResumeRequest(BaseModel):
    interruptId: str = Field(min_length=1, max_length=100)
    approved: StrictBool


def model_factory(options, route):
    return ChatOllama(model=MODEL, base_url=OLLAMA_URL,
        temperature=options.get("temperature", 0.1),
        num_predict=options.get("num_predict", 200 if route in ("finance", "buying") else 250),
        client_kwargs={"timeout": 180})


def engine():
    global _engine
    with _engine_lock:
        if _engine is None:
            database = os.environ.get("AI_CHECKPOINT_DB", str(Path.home() / ".local/share/agritech/checkpoints.sqlite"))
            _engine = Workflow(database, model_factory, Backend(os.environ.get("AI_BACKEND_URL", "http://localhost:8080")))
    return _engine


def protected(call):
    try:
        return call()
    except HTTPException:
        raise
    except ResponseError as exc:
        raise HTTPException(status_code=404 if exc.status_code == 404 else 502,
            detail="Configured Ollama model is unavailable") from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="The farming workflow is temporarily unavailable") from exc


@app.get("/health")
def health():
    try:
        Client(host=OLLAMA_URL, timeout=5).list()
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Ollama is unavailable") from exc
    return {"status": "ok", "framework": "langgraph", "model": MODEL,
        "tracingConfigured": os.getenv("AI_LANGSMITH_TRACING", "false").lower() == "true" and bool(os.getenv("LANGSMITH_API_KEY"))}


@app.post("/api/generate", response_model=GenerateResponse)
def generate(body: GenerateRequest):
    if body.model != MODEL:
        raise HTTPException(status_code=400, detail="Only the configured farming model is allowed")
    if body.stream:
        raise HTTPException(status_code=400, detail="This endpoint supports non-streaming replies only")
    return protected(lambda: GenerateResponse(model=MODEL, response=engine().legacy(body)))


@app.post("/api/conversations")
def create(authorization: str = Header(default="")):
    workflow = engine()
    return protected(lambda: workflow.create(workflow.backend.identity(authorization)))


@app.get("/api/conversations/{conversation_id}")
def get(conversation_id: UUID, authorization: str = Header(default="")):
    workflow = engine()
    return protected(lambda: workflow.get(workflow.backend.identity(authorization), str(conversation_id)))


@app.delete("/api/conversations/{conversation_id}")
def delete(conversation_id: UUID, authorization: str = Header(default="")):
    workflow = engine()
    return protected(lambda: workflow.delete(workflow.backend.identity(authorization), str(conversation_id)))


@app.post("/api/conversations/{conversation_id}/turn")
def turn(conversation_id: UUID, body: TurnRequest, authorization: str = Header(default="")):
    workflow = engine()
    action = body.action.model_dump(mode="json") if body.action else None
    result = protected(lambda: workflow.turn(workflow.backend.identity(authorization), str(conversation_id),
        authorization, body.prompt.strip() or "Review my group-order buying opportunity", action))
    result["model"] = MODEL if result["model"] == "Qwen" else result["model"]
    return result


@app.post("/api/conversations/{conversation_id}/resume")
def resume(conversation_id: UUID, body: ResumeRequest, authorization: str = Header(default="")):
    workflow = engine()
    return protected(lambda: workflow.resume(workflow.backend.identity(authorization), str(conversation_id),
        authorization, body.interruptId, body.approved))
