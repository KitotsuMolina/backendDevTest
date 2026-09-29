#!/usr/bin/env python3
"""Check the original mock scenarios against a running application (stdlib only)."""
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path

base_url = os.environ.get("APP_BASE_URL", "http://localhost:5000")
expected = {"1": ["2", "3", "4"], "2": ["3", "100", "1000"],
            "3": ["100", "1000"], "4": ["1", "2"], "5": ["1", "2"], "missing": None}
results = []
for product_id, ids in expected.items():
    start = time.monotonic()
    try:
        response = urllib.request.urlopen(f"{base_url}/product/{product_id}/similar", timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        payload = json.loads(body) if body else None
        elapsed = round(time.monotonic() - start, 3)
        assert response.status == (404 if ids is None else 200), (product_id, response.status)
        assert (None if payload is None else [p["id"] for p in payload]) == ids, (product_id, payload)
        assert elapsed < 8.5, (product_id, elapsed)
        results.append({"productId": product_id, "status": response.status,
                        "seconds": elapsed, "body": payload})
with urllib.request.urlopen(f"{base_url}/actuator/health", timeout=3) as response:
    health = json.load(response)
    assert health == {"status": "UP"}, health
    results.append({"health": health})
output = Path(__file__).resolve().parent.parent / "reports" / "manual.json"
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(results, indent=2) + "\n")
print(f"6 scenarios and health OK. Results: {output}")
