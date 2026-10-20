import os
import yaml
from pymongo import MongoClient
from dotenv import load_dotenv

# Load environment variables from .env
load_dotenv()

MONGO_USER = os.getenv("COMPANION_MONGO_USER", "companion_admin")
MONGO_PASS = os.getenv("COMPANION_MONGO_PASSWORD", "farmerCompanion03")
MONGO_HOST = os.getenv("COMPANION_MONGO_HOST", "localhost")
MONGO_PORT = os.getenv("COMPANION_MONGO_PORT", "27017")
MONGO_DB = os.getenv("COMPANION_MONGO_DB", "agritech_companion")

# Build connection URI
uri = f"mongodb://{MONGO_USER}:{MONGO_PASS}@{MONGO_HOST}:{MONGO_PORT}/?authSource=admin"

client = MongoClient(uri)
db = client[MONGO_DB]
collection = db["workflow_steps"]

# Path to your workflow YAML file
yaml_file_path = os.path.join("business-companion-workflow", "business-companion-workflow.yml")

if not os.path.exists(yaml_file_path):
    print(f"Error: Could not find {yaml_file_path}")
    exit(1)

with open(yaml_file_path, "r", encoding="utf-8") as f:
    data = yaml.safe_load(f)

# Extract steps list from YAML
steps = data.get("steps", []) if isinstance(data, dict) else data

if not steps:
    print("No steps found in YAML file.")
    exit(1)

# Clear existing steps and insert fresh data
collection.delete_many({})
result = collection.insert_many(steps)

print(f"Successfully seeded {len(result.inserted_ids)} workflow steps into MongoDB collection 'workflow_steps'!")