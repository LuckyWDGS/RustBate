@echo off
chcp 65001 >nul
echo ================================
echo   Echart API 前端服务启动
echo ================================
echo.

cd /d D:\Rust\echart-api\frontend

echo [1/2] 检查 Node.js 环境...
where npm >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到 npm，请先安装 Node.js
    pause
    exit /b 1
)

echo [2/2] 启动前端开发服务器...
echo.
echo 前端地址: http://localhost:5173
echo 后端地址: http://127.0.0.1:3000
echo.
echo 按 Ctrl+C 停止服务
echo ================================
echo.

npm run dev

pause
