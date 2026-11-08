# MaGo Orbital Cannon (Fabric 1.21.11)

Standalone server-side source project. Requires Fabric API, not OrbitalStrike+.

## Features
- Nuke: diamond blocks in corners, TNT on edges, fishing rod in center. 13 uneven visual rings; 13 TNT blasts per wave.
- Stab: TNT top/bottom, diamond blocks left/right, fishing rod center.
- Both rods are normal-looking vanilla fishing rods with custom names and custom-data tags, consumed when fired.
- MaGoMC can sneak + right-click a rod to add one copy to inventory. No commands or routine logging from this mod.
- Server-only. Other clients need no mod.

## Build
Install JDK 21 and Gradle 8.14+, then run `gradle build`. The output is `build/libs/mago-orbital-cannon-1.0.0.jar` (exclude any `-sources.jar`). Install it and a matching Fabric API on the Fabric 1.21.11 server.

## Important
This is an independent implementation, NOT an exact clone of OrbitalStrike+'s proprietary visual effects. The strike has not been play-tested or compiled in this environment. The version pins and mappings may need adjustment. TNT destruction may cause lag and damage builds; test on a backup first. Server console may still contain vanilla TNT/explosion warnings or errors. The username-based permission should be replaced with UUID-based authorization for security-sensitive deployments.
