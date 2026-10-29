import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from dotenv import load_dotenv
from pymongo import MongoClient
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate

load_dotenv()

# Explicitly define 'app' for ASGI/Uvicorn
app = FastAPI(
    title="The Business Companion AI Service",
    version="1.0.0",
    description="AI workflow guide for township retailers and farmers"
)

# MongoDB Setup
MONGO_USER = os.getenv("COMPANION_MONGO_USER", "companion_admin")
MONGO_PASS = os.getenv("COMPANION_MONGO_PASSWORD", "farmerCompanion03")
MONGO_HOST = os.getenv("COMPANION_MONGO_HOST", "localhost")
MONGO_PORT = os.getenv("COMPANION_MONGO_PORT", "27017")
MONGO_DB   = os.getenv("COMPANION_MONGO_DB", "agritech_companion")

mongo_uri = f"mongodb://{MONGO_USER}:{MONGO_PASS}@{MONGO_HOST}:{MONGO_PORT}/?authSource=admin"
client = MongoClient(mongo_uri)
db = client[MONGO_DB]
workflow_collection = db["workflow_steps"]

# OpenAI Model Setup
llm = ChatOpenAI(
    model="gpt-4o-mini",
    temperature=0.3
)

# System Prompt Definition
prompt_template = ChatPromptTemplate.from_messages([
    ("system", """You are 'The Business Companion', an empathetic and practical AI assistant for township retailers and small-scale farmers in South Africa.
Your objective is to guide users through the formalization and operational setup of their business step-by-step.

Context for Step {step_number} ({step_title}):
- Requirements: {ai_assistance}
- Official Authority: {official_source_name} ({official_source_url})
- Process Location: {where_to_do_it}

Instructions:
1. Answer the farmer/retailer's question clearly, practically, and grounded strictly in the current step context.
2. Keep advice actionable, referencing specific South African institutions (e.g., DWS, Municipal Health, CIPC) when applicable.
3. Keep tone supportive, encouraging, and easy to follow."""),
    ("user", "{user_message}")
])

class ChatRequest(BaseModel):
    step_number: int
    user_message: str

@app.get("/")
def health_check():
    return {"status": "online", "service": "Business Companion AI"}

@app.get("/api/v1/steps/{step_number}")
def get_step_context(step_number: int):
    step = workflow_collection.find_one({"stepNumber": step_number}, {"_id": 0})
    if not step:
        raise HTTPException(status_code=404, detail=f"Step {step_number} not found in workflow database.")
    return step

@app.post("/api/v1/chat")
def chat_with_companion(request: ChatRequest):
    step = workflow_collection.find_one({"stepNumber": request.step_number}, {"_id": 0})
    if not step:
        raise HTTPException(status_code=404, detail=f"Step {request.step_number} not found.")

    chain = prompt_template | llm
    
    response = chain.invoke({
        "step_number": step.get("stepNumber"),
        "step_title": step.get("title"),
        "ai_assistance": step.get("aiAssistance", ""),
        "official_source_name": step.get("officialSource", {}).get("name", "N/A"),
        "official_source_url": step.get("officialSource", {}).get("url", "N/A"),
        "where_to_do_it": step.get("whereToDoIt", "N/A"),
        "user_message": request.user_message
    })

    return {
        "step_number": request.step_number,
        "response": response.content
    }