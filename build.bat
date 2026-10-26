@echo off
chcp 65001 >nul
setlocal

REM 一键编译 NingguangSkip（Windows 双击即可运行）
REM 只要求本机装有 JDK 17，其余（Android SDK / Gradle 依赖）全部自动下载

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"

echo.
pause
