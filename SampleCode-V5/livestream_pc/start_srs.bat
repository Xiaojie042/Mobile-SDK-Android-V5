@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "CONF_FILE=%SCRIPT_DIR%srs\conf\srs_rtmp.conf"
set "SRS_BIN="

if exist "%SCRIPT_DIR%srs.exe" set "SRS_BIN=%SCRIPT_DIR%srs.exe"
if not defined SRS_BIN if exist "%SCRIPT_DIR%srs\bin\srs.exe" set "SRS_BIN=%SCRIPT_DIR%srs\bin\srs.exe"
if not defined SRS_BIN if exist "%SCRIPT_DIR%srs\objs\srs.exe" set "SRS_BIN=%SCRIPT_DIR%srs\objs\srs.exe"

if not defined SRS_BIN (
    echo [ERROR] srs.exe not found.
    echo Put SRS into one of these locations:
    echo   %SCRIPT_DIR%srs.exe
    echo   %SCRIPT_DIR%srs\bin\srs.exe
    echo   %SCRIPT_DIR%srs\objs\srs.exe
    echo.
    echo Then run this script again.
    pause
    exit /b 1
)

if not exist "%CONF_FILE%" (
    echo [ERROR] SRS config not found: %CONF_FILE%
    pause
    exit /b 1
)

echo [INFO] Starting SRS with:
echo   BIN : %SRS_BIN%
echo   CONF: %CONF_FILE%
echo.
echo [INFO] Expected RTMP publish URL:
echo   rtmp://192.168.3.31:1935/live/stream
echo.
echo [INFO] Keep this window open while testing.
echo.

"%SRS_BIN%" -c "%CONF_FILE%"

endlocal
