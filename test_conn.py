import urllib.request
import sys

def check_url(url):
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=5) as response:
            print(f"{url} -> {response.status}")
            print(response.read().decode('utf-8')[:200])
    except Exception as e:
        print(f"{url} -> Error: {e}")

check_url("http://127.0.0.1:3000/")
check_url("http://127.0.0.1:8080/api/auth/login")
check_url("http://127.0.0.1:8000/health")
check_url("http://localhost:3000/")
check_url("http://localhost:8080/")
check_url("http://localhost:8000/health")
