import os
import logging
from pathlib import Path
from urllib.parse import quote_plus
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from dotenv import load_dotenv
from pymongo import MongoClient
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

logger = logging.getLogger("business-companion")

# ==========================================
# 1. ENVIRONMENT & CONFIGURATION SETUP
# ==========================================
# Load settings from companion-ai-service/.env (this file is ignored by Git)
env_path = Path(__file__).parent / '.env'
load_dotenv(dotenv_path=env_path, override=True)


def require_env(name: str) -> str:
    """Return an environment variable, or stop with a clear message if it is missing."""
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"Missing {name}. Add it to companion-ai-service/.env")
    return value


app = FastAPI(
    title="The Business Companion AI Service",
    version="1.0.0",
    description="AI guide that helps farmers formalise their business, step by step"
)

# CORS: only the local frontend may call this service from a browser.
# (Calls through the Vite proxy do not need CORS, but this keeps direct calls working in development.)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==========================================
# 2. MONGODB DATABASE CONNECTION
# ==========================================
# The username and password have no defaults on purpose: they must come from .env
MONGO_USER = require_env("COMPANION_MONGO_USER")
MONGO_PASS = require_env("COMPANION_MONGO_PASSWORD")
MONGO_HOST = os.getenv("COMPANION_MONGO_HOST", "localhost")
MONGO_PORT = os.getenv("COMPANION_MONGO_PORT", "27017")
MONGO_DB = os.getenv("COMPANION_MONGO_DB", "agritech_companion")

# quote_plus makes special characters in the login safe inside the address
mongo_uri = (
    f"mongodb://{quote_plus(MONGO_USER)}:{quote_plus(MONGO_PASS)}"
    f"@{MONGO_HOST}:{MONGO_PORT}/?authSource=admin"
)
client = MongoClient(mongo_uri)
db = client[MONGO_DB]
workflow_collection = db["workflow_steps"]

# ==========================================
# 3. PROMPT TEMPLATE DEFINITION
# ==========================================
prompt_template = ChatPromptTemplate.from_messages([
    ("system", """You are 'The Business Companion', a friendly and practical assistant that helps small-scale farmers in South Africa turn their farm into a registered business, one step at a time.

Context for Step {step_number} ({step_title}):
- What this step involves: {ai_assistance}
- Official source: {official_source_name} ({official_source_url})
- Where to do it: {where_to_do_it}

Rules:
1. Answer the farmer's question in simple, plain words, as if explaining to someone starting their first business. Avoid jargon, and explain any term you must use.
2. Use farming examples (crops, livestock, land, seasons, selling produce) where they help.
3. Keep answers short, about 150 words at most. Give the most useful points first, then offer to go deeper or ask one follow-up question.
4. Links: only give the official link shown above. Never make up or guess a website address. If the link above is empty, None or N/A, say there is no official website for this step and tell the farmer where to go in words (for example "your bank" or "an accountant").
5. Do not state specific fees, deadlines, thresholds or legal requirements unless they appear in the step context above. If you are not sure, say so and tell the farmer to check with the official source or an accountant.
6. You give guidance, not legal, tax or financial advice. For decisions about business structure, tax or VAT, suggest speaking to an accountant.
7. You cannot register anything for the farmer. You explain how they can do it themselves.
8. If the question is not about starting or running a farm business, politely say you can only help with that.
9. Be warm, encouraging and easy to follow."""),
    ("user", "{user_message}")
])

# ==========================================
# 4. PYDANTIC REQUEST MODELS
# ==========================================
class ChatRequest(BaseModel):
    step_number: int
    user_message: str

# ==========================================
# 5. API ENDPOINTS
# ==========================================

@app.get("/")
def health_check():
    """Simple heartbeat check to verify the AI service is online."""
    return {"status": "online", "service": "Business Companion AI"}


@app.get("/api/v1/steps")
def list_steps():
    """Returns all workflow steps in order, without internal notes."""
    steps = workflow_collection.find({}, {"_id": 0, "note": 0}).sort("stepNumber", 1)
    return list(steps)


@app.get("/api/v1/steps/{step_number}")
def get_step_context(step_number: int):
    """Fetches the configuration and requirements for a specific workflow step from MongoDB."""
    step = workflow_collection.find_one({"stepNumber": step_number}, {"_id": 0})
    if not step:
        raise HTTPException(status_code=404, detail=f"Step {step_number} not found in workflow database.")
    return step


@app.post("/api/v1/chat")
def chat_with_companion(request: ChatRequest):
    """Answers a farmer's question about one workflow step."""
    step = workflow_collection.find_one({"stepNumber": request.step_number}, {"_id": 0})
    if not step:
        raise HTTPException(status_code=404, detail=f"Step {request.step_number} not found in workflow database.")

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=500, detail="The AI model is not configured on the server.")

    source = step.get("officialSource") or {}

    llm = ChatOpenAI(
        model="gpt-4o-mini",
        temperature=0.3,
        api_key=api_key
    )
    chain = prompt_template | llm

    try:
        response = chain.invoke({
            "step_number": step.get("stepNumber"),
            "step_title": step.get("title"),
            "ai_assistance": step.get("aiAssistance") or "N/A",
            "official_source_name": source.get("name") or "N/A",
            "official_source_url": source.get("url") or "none",
            "where_to_do_it": step.get("whereToDoIt") or "N/A",
            "user_message": request.user_message
        })
    except Exception:
        logger.exception("The AI model call failed")
        raise HTTPException(status_code=502, detail="The AI model could not answer right now. Please try again.")

    return {
        "step_number": request.step_number,
        "response": response.content
    }