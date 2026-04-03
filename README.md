# TraceTech 

AI-powered canteen demand forecasting system.

##  Architecture
React → Spring Boot → FastAPI (ML)

##  Tech Stack
- Frontend: React
- Backend: Spring Boot
- ML API: FastAPI
- Database: MySQL

##  Project Structure
- backend/ → Spring Boot API
- fastapi/ → ML model service
- tracetech-react/ → Frontend

##  Features
- Demand forecasting
- Weather-based prediction
- Sales tracking
- Menu management

##  How to Run

Set these environment variables before starting the services:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `WEATHER_API_KEY` (optional; mock weather is used when omitted)
- `ML_SERVICE_URL` (optional; defaults to `http://localhost:8000`)

### Backend
cd backend  
mvn spring-boot:run  

### FastAPI
cd fastapi  
uvicorn main:app --reload  

### Frontend
cd tracetech-react\tracetech  
npm install  
npm start  
