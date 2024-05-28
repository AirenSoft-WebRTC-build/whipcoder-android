@echo off
setlocal

set "file1=Z:\webrtc-build-private\webrtc-working\src\libwebrtc.aar"
set "file2=C:\Development\webrtc-whip-android-private\src\app\libs\libwebrtc.aar"

:monitor_loop
timeout /t 1 /nobreak >nul 2>&1

for /f %%A in ('powershell "(Get-Item '%file1%').LastWriteTime.ToString('yyyyMMddHHmmss')"') do set "date1=%%A"
for /f %%A in ('powershell "(Get-Item '%file2%').LastWriteTime.ToString('yyyyMMddHHmmss')"') do set "date2=%%A"

echo "%date1%"
echo "%date2%"

if NOT "%date1%" == "%date2%" (
    echo Changed date
    copy "%file1%" "%file2%"
)


goto monitor_loop

endlocal