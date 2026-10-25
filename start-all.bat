@echo off
setlocal
cd /d "%~dp0"

echo [Java Blueprints] Stopping any lingering processes on ports 8080 and 5173...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8080 .*LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173 .*LISTENING"') do taskkill /F /PID %%a >nul 2>&1

if not exist "%~dp0frontend\node_modules\" (
  echo [Java Blueprints] Installing frontend dependencies (first launch)...
  cmd /c "cd /d "%~dp0frontend" && call npm install"
)

echo [Java Blueprints] Starting backend (Ktor + Netty on JDK 21)...
start "Java Blueprints Backend" cmd /c "cd /d "%~dp0Backend" && call gradlew.bat run --no-daemon"

echo [Java Blueprints] Waiting for backend health endpoint (http://localhost:8080/api/health)...
set /a attempts=0
:wait_backend
set /a attempts+=1
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/health -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } } catch {} ; exit 1"
if %errorlevel% equ 0 goto backend_ready
if %attempts% geq 60 (
  echo ERROR: Backend did not become ready within 120 seconds.
  echo Please check if JDK 21 is available and port 8080 is accessible.
  pause
  exit /b 1
)
timeout /t 2 /nobreak >nul
goto wait_backend

:backend_ready
echo [Java Blueprints] Backend is healthy! Starting frontend (Vite)...
start "Java Blueprints Frontend" cmd /c "cd /d "%~dp0frontend" && npm run dev -- --host localhost"
timeout /t 3 /nobreak >nul
echo [Java Blueprints] Opening application in browser at http://localhost:5173
start "" http://localhost:5173
endlocal
