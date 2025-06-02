@echo off
REM Start script for local development

echo Starting Password Generator Tool for local development...

REM Start the Spring Boot backend
start cmd /k "cd %~dp0 && mvn spring-boot:run"

REM Wait for backend to initialize
timeout /t 10

REM Start the React frontend
start cmd /k "cd %~dp0\ui\password-generator-tool && npm run dev"

echo Application started!
echo Backend: http://localhost:5000
echo Frontend: http://localhost:3000
