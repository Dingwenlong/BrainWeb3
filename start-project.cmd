@echo off
setlocal
title BrainWeb3 One-Click Start
pushd "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\bootstrap\start-on-new-machine.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo BrainWeb3 启动失败，错误码: %EXIT_CODE%
  pause
)

popd
exit /b %EXIT_CODE%
