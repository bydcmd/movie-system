@echo off
chcp 65001 >nul
echo ================================
echo   Movie System 启动脚本
echo ================================

:: 启动后端 (在后台运行)
echo.
echo [1/2] 启动后端 (Spring Boot)...
cd /d "%~dp0backend"
start "MovieBackend" cmd /c "mvn spring-boot:run"

:: 等待后端启动
timeout /t 15 /nobreak >nul

:: 启动前端
echo.
echo [2/2] 启动前端 (Vite Dev Server)...
cd /d "%~dp0frontend"
start "MovieFrontend" cmd /c "pnpm dev"

echo.
echo ================================
echo   启动完成！
echo   后端: http://localhost:8080
echo   前端: http://localhost:5173
echo   Swagger: http://localhost:8080/swagger-ui.html
echo ================================
pause
