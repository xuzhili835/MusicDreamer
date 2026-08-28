@echo off
cd /d "%~dp0"

echo 停止 MusicDreamer Docker 全栈...
docker compose stop
if errorlevel 1 (
    echo [失败] 停止出错，见上方报错。
    pause
    exit /b 1
)
echo.
echo 已全部停止，数据都保留着：
echo   - 再次启动：双击 docker-up.cmd（容器秒级恢复，无需重新迁移数据）
echo   - 彻底删容器（数据仍在 mysql_data 卷里，不丢）：docker compose down
echo   - 前端 vite 若在独立窗口开着，直接关掉那个窗口即可
pause
