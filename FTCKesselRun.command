#!/usr/bin/env bash
# FTCKesselRun. Double-click this.
#
# If nothing happens, the zip did not carry the executable bit -- macOS is strict about that.
# In Terminal:  chmod +x FTCKesselRun.command
cd "$(dirname "$0")" || exit 1
echo "Starting FTCKesselRun. Your browser will open in a moment."
echo "Closing this window stops the simulator."
echo
# The separator is ':' here and ';' on Windows; this launcher only ever runs on macOS and
# Linux, so it is a colon. ftckesselrun.jar first -- see the note in tools/dist.sh.
./jre/bin/java -cp "lib/ftckesselrun.jar:lib/*" org.firstinspires.ftc.simulator.LiveServer "$@"
status=$?
if [ $status -ne 0 ]; then
  echo
  echo "FTCKesselRun stopped with an error. The lines above say why."
  read -r -p "Press Return to close. " _
fi
