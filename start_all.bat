@echo off
echo Starting TraceTech Services...

cd /d "%~dp0fastapi"
start "FastAPI Service (8000)" cmd /k "call venv\Scripts\activate.bat && uvicorn main:app --port 8000"

cd /d "%~dp0backend"
start "Spring Boot Backend (8080)" cmd /k "mvn spring-boot:run"

cd /d "%~dp0tracetech-react\tracetech"
start "React Frontend (3000)" cmd /k "npm start"

echo All services are launching in their own windows!
