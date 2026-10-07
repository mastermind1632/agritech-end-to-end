"""Internal LangChain adapter; the finance API supplies retrieved farm context."""
import os

os.environ.setdefault("LANGSMITH_TRACING", "false")

from fastapi import FastAPI, HTTPException
from langchain.agents import create_agent
from langchain_core.output_parsers import StrOutputParser
from langchain_ollama import ChatOllama
from ollama import Client, ResponseError
from pydantic import BaseModel, Field

MODEL = os.environ.get("AI_MODEL", "qwen2.5:0.5b")
OLLAMA_URL = os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
app = FastAPI(title="AgriTech LangChain Service", version="1.0.0")


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


@app.get("/health")
def health() -> dict:
    try:
        Client(host=OLLAMA_URL, timeout=5).list()
    except Exception as exc:
        raise HTTPException(status_code=503, detail="Ollama is unavailable") from exc
    return {"status": "ok", "framework": "langchain", "model": MODEL}


@app.post("/api/generate", response_model=GenerateResponse)
def generate(body: GenerateRequest) -> GenerateResponse:
    if body.model != MODEL:
        raise HTTPException(status_code=400, detail="Only the configured farming model is allowed")
    if body.stream:
        raise HTTPException(status_code=400, detail="This endpoint supports non-streaming replies only")
    try:
        llm = ChatOllama(
            model=MODEL,
            base_url=OLLAMA_URL,
            temperature=body.options.temperature,
            num_predict=body.options.num_predict,
            client_kwargs={"timeout": 180},
        )
        # Empty tools work with the installed small Qwen model without native tool calling.
        # No shared checkpointer: farm context cannot leak into another user's conversation.
        agent = create_agent(model=llm, tools=[], system_prompt=body.system)
        result = agent.invoke(
            {"messages": [{"role": "user", "content": body.prompt}]},
            config={"recursion_limit": 4},
        )
        answer = StrOutputParser().invoke(result["messages"][-1]).strip()
        if not answer:
            raise HTTPException(status_code=502, detail="Ollama returned an empty response")
        return GenerateResponse(model=MODEL, response=answer)
    except HTTPException:
        raise
    except ResponseError as exc:
        # The finance API retains its missing-model pull/retry behaviour.
        status = 404 if exc.status_code == 404 else 502
        raise HTTPException(status_code=status, detail="Configured Ollama model is unavailable") from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail="LangChain could not reach the AI model") from exc
