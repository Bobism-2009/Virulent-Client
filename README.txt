Virulent Client
===============

Minecraft Fabric utility client
Version: 1.10.7
Minecraft: 26.1.2
Java: 25+
Fabric Loader: 0.19.3+
Fabric API: required (0.155.2+26.1.2 or matching 26.1.2 build)

Press Right Shift in-game to open the Click GUI.


Install
-------
1) Install Fabric for Minecraft 26.1.2
   https://fabricmc.net/use/installer/
   - Minecraft version: 26.1.2
   - Install, then select the Fabric 26.1.2 profile in the launcher

2) Install Java 25 (required for this Minecraft version)
   https://adoptium.net/

3) Open the mods folder
   Windows: Win+R -> %appdata%\.minecraft\mods

4) Put these jars in mods:
   - fabric-api for 26.1.2
     https://modrinth.com/mod/fabric-api/versions
   - virulent-client-1.10.7.jar
     (from this project: build\libs\virulent-client-1.10.7.jar)

5) Launch the Fabric 26.1.2 profile (not vanilla).

6) In game, press Right Shift.


What to send someone else
-------------------------
Send ALL of these:

1) virulent-client-1.10.7.jar
   Path: build\libs\virulent-client-1.10.7.jar
   Do NOT send:
   - virulent-client-1.10.7-sources.jar
   - anything from run\
   - project source folders

2) Fabric API for Minecraft 26.1.2

3) This README.txt


Click GUI
---------
- Toggle: Right Shift
- Left click module: enable / disable
- Right click module: expand settings
- Middle click module: set keybind (when Keys is on)
- Footer / toolbar:
  - Color square: cycle accent color
  - Layout label: Default / Meteor / Wurst
  - Desc / Keys / Hud toggles
  - Width slider (Default layout)

Layouts
-------
Default  - Single Virulent window with sidebar
Meteor   - Floating category panels (drag; right-click header to collapse)
Wurst    - Classic column windows


Modules (overview)
------------------
Combat:   KillAura, Velocity, TriggerBot, AutoClicker, AutoTotem
Movement: Sprint, Flight, Speed, Step, AirJump, NoFall, Jesus, NoSlow
Render:   Fullbright, ESP, Tracers, ArmorHud, NoHurtCam, NoFire, Xray
Player:   FastPlace, FastBreak, AutoTool, TreeBot, Tunneler, NoInteract
Misc:     Zoom, Freecam, Teleport, Panic

ArmorHud shows equipped armor with durability (Bar / Percent / Both / None).


Config
------
Saved under:
  .minecraft\virulent\

Typical files:
  config.json  - module settings / keybinds
  gui.json     - Click GUI layout, theme, window position, HUD


Build from source
-----------------
Requirements:
  - JDK 25
  - Internet (Gradle downloads Minecraft / Fabric deps)

From the project folder:

  gradlew.bat clean build

Output:
  build\libs\virulent-client-1.10.7.jar


Common issues
-------------
1) Fabric API missing
   Virulent will not load without Fabric API for 26.1.2.

2) Wrong Minecraft version
   This build is for 26.1.2 only.

3) Wrong Java version
   Use Java 25+. Older JDKs will fail.

4) Launched vanilla instead of Fabric
   Launcher profile must be Fabric 26.1.2.

5) Sent the sources jar
   Use virulent-client-1.10.7.jar, not -sources.jar.

6) Old jar
   Rebuild with: gradlew.bat clean build
   Then copy the new jar from build\libs\


Checklist
---------
[ ] Minecraft 26.1.2
[ ] Java 25+
[ ] Fabric Loader profile for 26.1.2
[ ] fabric-api ...26.1.2.jar in mods
[ ] virulent-client-1.10.7.jar in mods
[ ] Launched Fabric profile
[ ] Right Shift opens Click GUI
