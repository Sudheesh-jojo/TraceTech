import urllib.request
import json
import os

def fetch_json(url, token=None, method="GET", body=None):
    try:
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        data = None
        if body:
            data = json.dumps(body).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        return {"error": str(e)}

print("Logging in...")
login_res = fetch_json("http://localhost:8080/api/auth/login", method="POST", body={"email":"test@rit.ac.in", "password":"test1234"})
token = login_res.get("token") if login_res else None

out = {}
if token:
    out["forecast_today"] = fetch_json("http://localhost:8080/api/forecast/today", token)
    out["impact_summary"] = fetch_json("http://localhost:8080/api/impact/summary", token)

with open(r"c:\Users\Admin\OneDrive\Documents\backend\api_dump.json", "w") as f:
    json.dump(out, f, indent=2)
print("Dumped API data to api_dump.json")
