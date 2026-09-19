FTCKesselRun
==============

Double-click FTCKesselRun.cmd on Windows, FTCKesselRun.command on a Mac, or run
./FTCKesselRun.sh on Linux. All three do the same thing; the extension is only there so
that each operating system's file manager will run it.

Your browser opens on a page that asks which TeamCode you want to drive. Choose the folder
you cloned FtcRobotController into -- or the TeamCode folder inside it, or TeamCode\src\main\
java, whichever you think of as your project. It works out the rest and remembers it, so next
time your project is one click on the list.

Then pick an OpMode, press INIT, press Start, and drive. A game controller is used if one is
plugged in; otherwise WASD drives and Q/E turns.

Nothing needs to be installed. Java is inside this folder.


Building a robot
----------------
Open http://127.0.0.1:8730/robot and go to "6 - Garage". Click parts out of the toolbox, drag
them into place, adjust their settings, and press "Save and drive it". The Code panel writes the
Java your OpMode needs -- device names included, so nothing gets retyped and misspelled.

Your own parts go in the parts folder next to this file. Open parts/README.md for the format:
measure a part once, write down where the numbers came from, and it is in the toolbox for
everyone you send this to.


The first time you open a project
---------------------------------
It downloads your team's libraries -- Pedro Pathing, FTCLib, whatever your build.gradle asks
for. That needs the internet and takes about ten seconds. After that they are kept, and
opening the same project again works with no connection at all.


If Windows says it protected your PC
------------------------------------
That is because this was downloaded rather than installed. Click "More info", then "Run
anyway". If you can, right-click the ZIP and choose Properties -> Unblock BEFORE extracting
it, and Windows will not ask.


If it says the port is already in use
-------------------------------------
FTCKesselRun is already running. Look for another window of it, or open
http://127.0.0.1:8730 and see. To run a second copy on purpose, start it with:  --port 8750


Watching from another machine
-----------------------------
By default this listens only on the computer it is running on, so Windows never asks to let
Java through the firewall. If a coach wants the driver station on a phone on the same wifi,
start it with --lan -- and then Windows will ask, and it needs somebody who can say yes.


Where it keeps things
---------------------
%LOCALAPPDATA%\FTCKesselRun
  settings.json   the list of projects you have opened
  libs\           your teams' downloaded libraries, one folder per project

Deleting that folder loses the list and re-downloads the libraries. It breaks nothing.
