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

### Backend
cd backend  
mvn spring-boot:run  

### FastAPI
cd fastapi  
uvicorn main:app --reload  

### Frontend
cd tracetech-react  
npm install  
npm start  
