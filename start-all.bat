@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo [Java Blueprints] Stopping any lingering processes on ports 8080 and 5173...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8080,5173 -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"

if not exist "%~dp0frontend\node_modules\" (
  echo [Java Blueprints] Installing frontend dependencies...
  cd /d "%~dp0frontend"
  call npm install
  cd /d "%~dp0"
)

echo [Java Blueprints] Starting backend (Ktor + Netty on JDK 21)...
start "Java Blueprints Backend" cmd /k "cd /d "%~dp0Backend" && gradlew.bat run --no-daemon"

echo [Java Blueprints] Waiting for backend health endpoint (http://localhost:8080/api/health)...
set /a attempts=0
:wait_backend
set /a attempts+=1
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/health -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } } catch {} ; exit 1"
if %errorlevel% equ 0 goto backend_ready
if %attempts% geq 60 (
  echo ERROR: Backend did not become ready within 120 seconds.
  pause
  exit /b 1
)
ping 127.0.0.1 -n 3 >nul
goto wait_backend

:backend_ready
echo [Java Blueprints] Backend is healthy! Starting frontend (Vite)...
start "Java Blueprints Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev -- --host localhost"
ping 127.0.0.1 -n 4 >nul
echo [Java Blueprints] Opening application in browser at http://localhost:5173
start "" http://localhost:5173
endlocal
