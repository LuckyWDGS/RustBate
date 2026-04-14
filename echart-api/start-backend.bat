@echo off
chcp 65001 >nul
echo ================================
echo   Echart API 后端服务启动
echo ================================
echo.

cd /d D:\Rust\echart-api

echo [1/2] 检查 Rust 环境...
where cargo >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到 Cargo，请先安装 Rust
    pause
    exit /b 1
)

echo [2/2] 启动后端服务...
echo.
echo 服务地址: http://127.0.0.1:3000
echo API 端点:
echo   - GET /api/power/data  (供电模块)
echo   - GET /api/mow/data    (工务模块)
echo.
echo 按 Ctrl+C 停止服务
echo ================================
echo.

cargo run

pause
