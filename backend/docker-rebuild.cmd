@echo off
setlocal
cd /d "%~dp0"

echo ================================================
echo   重新打包并更新 Docker 镜像（改了 Java 代码后用）
echo ================================================
set "JAVA_HOME=C:\Users\legion\.jdks\openjdk-22.0.2"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/2] Maven 打包（跳过测试）...
call tools\apache-maven-3.9.16\bin\mvn.cmd -s tools\settings.xml -DskipTests package
if errorlevel 1 (
    echo [失败] Maven 打包失败，见上方报错。
    pause
    exit /b 1
)
echo [2/2] 重建镜像并滚动更新（只重建 jar 有变化的模块）...
docker compose up -d --build mysql redis nacos gateway mod-login mod-music mod-setting mod-upload mod-songlist mod-like mod-media
if errorlevel 1 (
    echo [失败] 镜像构建/更新失败，见上方报错。
    pause
    exit /b 1
)
echo.
echo 完成，改动已上线。前端刷新页面即可（网关仍是 8080）。
docker compose ps
echo.
pause
