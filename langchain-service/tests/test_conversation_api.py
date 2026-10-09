from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from fastapi import HTTPException
from fastapi.testclient import TestClient
from app.main import app
from app.workflow import Workflow
from app.telemetry import Telemetry
from test_chat import FakeModel
from test_workflow import FakeBackend


class AuthBackend(FakeBackend):
    def identity(self,bearer):
        if bearer not in ("Bearer a","Bearer b"):
            raise HTTPException(status_code=401,detail="Sign in")
        return bearer[-1]


class ConversationApiTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.workflow=Workflow(str(Path(self.tmp.name)/"state.sqlite"),
            lambda options,route:FakeModel(),AuthBackend(),Telemetry(enabled=False))
        self.patch=patch("app.main._engine",self.workflow);self.patch.start()
        self.client=TestClient(app)
        self.headers={"Authorization":"Bearer a"}

    def tearDown(self):
        self.patch.stop();self.workflow.connection.close();self.workflow.checkpoint_connection.close();self.tmp.cleanup()

    def test_requires_authenticated_owner_on_every_operation(self):
        self.assertEqual(self.client.post("/api/conversations").status_code,401)
        response=self.client.post("/api/conversations",headers=self.headers)
        thread=response.json()["conversationId"];path="/api/conversations/"+thread
        self.assertEqual(self.client.get(path,headers={"Authorization":"Bearer b"}).status_code,404)
        self.assertEqual(self.client.delete(path,headers={"Authorization":"Bearer b"}).status_code,404)
        self.assertEqual(self.client.post(path+"/turn",headers={"Authorization":"Bearer b"},
            json={"prompt":"seed","owner":"a"}).status_code,404)
        self.assertEqual(self.client.get(path,headers=self.headers).status_code,200)

    def test_validates_actions_and_boolean_approvals(self):
        thread=self.client.post("/api/conversations",headers=self.headers).json()["conversationId"]
        path="/api/conversations/"+thread
        for body in ({},{"action":{"orderId":"invalid","quantity":1}},{"prompt":"x"*2001}):
            self.assertEqual(self.client.post(path+"/turn",headers=self.headers,json=body).status_code,422)
        self.assertEqual(self.client.post(path+"/resume",headers=self.headers,
            json={"interruptId":"pending","approved":"yes"}).status_code,422)
