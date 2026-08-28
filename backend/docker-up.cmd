@echo off
setlocal
cd /d "%~dp0"

echo ================================================
echo   MusicDreamer Docker 一键启动
echo ================================================

REM ---------- 1/4 Docker 引擎 ----------
docker info >nul 2>&1
if not errorlevel 1 goto engine_ok
echo [1/4] Docker 引擎未就绪，正在拉起 Docker Desktop（冷启动约 30-60 秒）...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
set /a tries=0
:wait_engine
ping -n 4 127.0.0.1 >nul
docker info >nul 2>&1
if not errorlevel 1 goto engine_ok
set /a tries+=1
if %tries% lss 40 goto wait_engine
echo [失败] 等待 Docker 引擎超时。请手动打开 Docker Desktop，等鲸鱼图标稳定后再运行本脚本。
pause
exit /b 1
:engine_ok
echo [1/4] Docker 引擎就绪。

REM ---------- 2/4 起容器 ----------
echo [2/4] docker compose up -d ...
docker compose up -d mysql redis nacos gateway mod-login mod-music mod-setting mod-upload mod-songlist mod-like mod-media
if errorlevel 1 (
    echo [失败] compose 启动出错，见上方报错信息。
    pause
    exit /b 1
)

REM ---------- 3/4 等服务注册进 Nacos ----------
echo [3/4] 等待 8 个服务注册进 Nacos（一般 1-2 分钟）...
set /a tries=0
:wait_svc
set CNT=0
for /f %%i in ('powershell -NoProfile -Command "(Invoke-RestMethod 'http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=20' -TimeoutSec 4).count" 2^>nul') do set CNT=%%i
if %CNT% GEQ 8 goto svc_ok
set /a tries+=1
if %tries% geq 48 goto svc_timeout
ping -n 6 127.0.0.1 >nul
goto wait_svc
:svc_timeout
echo [提醒] 4 分钟内未集齐 8 个服务（当前 %CNT% 个）。
echo        用 docker compose ps 看是否有容器在反复重启，或查《Docker使用教程》坑表。
goto summary
:svc_ok
echo [3/4] 8/8 服务全部注册完成。

REM ---------- 4/4 前端 ----------
set FECNT=0
for /f %%i in ('powershell -NoProfile -Command "@(Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue).Count" 2^>nul') do set FECNT=%%i
if %FECNT% GEQ 1 (
    echo [4/4] 前端已在运行（5173 端口占用中），跳过。
    goto summary
)
if not exist "%~dp0..\frontend\node_modules" (
    echo [4/4] 前端依赖未安装，跳过。需要时：cd frontend 后执行 npm install 和 npm run dev
    goto summary
)
echo [4/4] 已在新窗口启动前端 vite（关掉那个窗口 = 停前端）...
start "MusicDreamer-frontend" cmd /k "cd /d %~dp0..\frontend && npm run dev"

:summary
echo.
echo ================================================
echo   全栈已就绪，浏览器访问：
echo     前端演示      http://localhost:5173
echo     网关 API      http://localhost:8080
echo     Nacos 控制台  http://localhost:8848/nacos  账号 nacos/nacos
echo   停止：双击 docker-down.cmd（数据保留）
echo ================================================
docker compose ps
echo.
pause
