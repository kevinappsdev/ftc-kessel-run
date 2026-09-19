@echo off
REM FTCKesselRun. Double-click this.
REM
REM cd /d %~dp0 so relative paths work, but the program does not rely on it: Install.java
REM finds where it lives from its own jar. This is belt and braces, and it costs one line.
cd /d "%~dp0"
title FTCKesselRun
echo Starting FTCKesselRun. Your browser will open in a moment.
echo Closing this window stops the simulator.
echo.
"%~dp0jre\bin\java.exe" -cp "%~dp0lib\ftckesselrun.jar;%~dp0lib\*" org.firstinspires.ftc.simulator.LiveServer %*
REM Held open on failure so the message above the crash is readable. A window that appears
REM and vanishes is the least useful thing a program can do.
if errorlevel 1 (
  echo.
  echo FTCKesselRun stopped with an error. The lines above say why.
  pause
)
