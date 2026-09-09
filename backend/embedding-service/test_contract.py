import json
import unittest
from pathlib import Path

from fastapi.testclient import TestClient

import app as embedding_app


CONTRACT_ROOT = Path(__file__).resolve().parents[2] / "contracts" / "search-embedding"


class FakeVectors:
    def __init__(self, values):
        self.values = values

    def astype(self, _dtype):
        return self

    def tolist(self):
        return self.values


class FakeModel:
    def encode(self, texts, **options):
        if options != {
            "normalize_embeddings": True,
            "convert_to_numpy": True,
            "show_progress_bar": False,
        }:
            raise AssertionError(f"unexpected inference options: {options}")
        if texts != ["synthetic red shoe", "synthetic blue bag"]:
            raise AssertionError(f"unexpected contract texts: {texts}")
        return FakeVectors([[1.0, 0.0, 0.0], [0.0, 1.0, 0.0]])


class EmbeddingProviderContractTest(unittest.TestCase):
    def test_accepts_request_and_produces_response_consumed_by_search(self):
        request = json.loads((CONTRACT_ROOT / "embedding-request.json").read_text())
        expected = json.loads((CONTRACT_ROOT / "embedding-response.json").read_text())
        embedding_app.model = FakeModel()
        embedding_app.loaded_model_source = "synthetic-contract-model"

        response = TestClient(embedding_app.app).post("/api/v1/embeddings", json=request)

        self.assertEqual(200, response.status_code)
        self.assertEqual(expected, response.json())


if __name__ == "__main__":
    unittest.main()
