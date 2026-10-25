@echo off
echo ========================================
echo Java Blueprints Backend - Quick Start
echo ========================================
echo.

echo [1/3] Checking Java version...
java -version
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Please install JDK 17+
    pause
    exit /b 1
)

echo.
echo [2/3] Freeing port 8080 if already in use...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1

echo.
echo [3/3] Starting Ktor server on http://localhost:8080 ...
echo Press Ctrl+C to stop
echo.
call gradlew.bat run --no-daemon

pause
