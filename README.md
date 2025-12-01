<div align=center><h1>RoadWeaver</h1></div>

<p align="center">
  <a href="https://modrinth.com/mod/roadweaver"><img src="https://img.shields.io/modrinth/dt/roadweaver?logo=modrinth&label=Modrinth&color=1bd96a&style=flat-square" alt="Modrinth Downloads"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/roadweaver"><img src="https://img.shields.io/curseforge/dt/1358489?logo=curseforge&label=CurseForge&color=f16436&style=flat-square" alt="CurseForge Downloads"></a>
  <a href="https://discord.gg/tUJMJkbbr2"><img src="https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white&style=flat-square" alt="Discord"></a>
  <a href="https://github.com/shiroha-233/RoadWeaver"><img src="https://img.shields.io/github/stars/shiroha-233/RoadWeaver?logo=github&style=flat-square" alt="GitHub Stars"></a>
</p>

<div align=center><h4>English | <a href="README_CN.md">简体中文</a></h4></div>

<table>
  <tr>
    <td><img src="image/img1.png" width="2000"></td>
    <td><img src="image/img2.png" width="2000"></td>
    <td><img src="image/img3.png" width="2000"></td>
  </tr>
</table>

<div align=center><h3>A Minecraft mod that automatically generates beautiful roads between villages or custom structures.</h3></div>

## ✨Key Features

### 1. Smart Road Generation

- **Intelligent Pathfinding**: Multiple pathfinding algorithms that avoid steep and dangerous areas; adjusts routes based on terrain height, biomes, and ground stability
- **Bezier Curves**: Applies Bezier curve smoothing to polyline paths, creating natural smooth curves and avoiding sharp turns
- **Multiple Road Types**:
  - Artificial roads: (stone bricks, slabs), (dirt, mud bricks), etc., or customize your own in the preset editor
  - Natural roads: Biome-adaptive road materials
- **Obstacle Avoidance**: Cuts, fills terrain and removes trees to ensure road passability
- **Tunnel & Bridge System**: Tunnels through mountains, bridges over water
- **Slab System**: Fills slabs at elevation changes to improve passability
- **Road Foundation**: Smoothly interpolates surrounding height field based on road elevation, fills foundation only when road is above original terrain, creating natural convex slopes that blend seamlessly with the landscape

### 2. Decoration System

- **Lamp System**: Redstone lamps with automatic day/night control
- **Signpost System**: Distance markers and directional signs
- **Roadside Structure Decorations**: Randomly generates benches, campfires and other decorative structures along roads

### 3. Configuration Options

- **Performance Optimization**: Multi-threaded async generation with concurrency control; height and terrain caching to reduce redundant calculations
- **Multiple Network Planning Algorithms**: KNN (sparsest) / Delaunay (densest) / RNG (balanced)
- **Multiple Pathfinding Algorithms**: A* / Bidirectional A* / Fluid Simulation
- **Road Block Customization**: Mix and match in the preset editor

### 4. Visualization Tools

- **Visual Debugging**: Road network map; status colors (planned/generating/completed/failed); interactions (drag, zoom, right-click teleport); statistics for road count, length and status
- **Manual Link Mode**: Plan road networks according to your preferences

## ⚡️Compatibility

- New versions (2.0.0+) completely abandon the old `/locate` command search mechanism, no longer blocking the game main thread
- Structure prediction, road network planning and pathfinding all run in dedicated thread pools; main thread only handles driving and result application

### ⚡️Known Compatibility & Performance Issues

- This mod (2.0.6+) is compatible with Tectonic-V2 / Epic Terrain / Terralith, but **incompatible with Tectonic-V3**
- This mod relies on vanilla mechanics, so it's incompatible with mods that overhaul vanilla mechanics like TerraFirmaCraft: The Next Generation

**Incompatibility symptoms:**
- Extremely slow road generation
- Very slow or blank map data loading
- Map shows roads as generated but nothing appears when approaching

**Performance factors:**
- World terrain complexity
- Pathfinding step size and weight configuration
- Concurrent road generation count and thread pool size

## 📌Usage

- **Auto Generation**: Roads automatically generate between structures after entering the world
- **Road Network Map**: Press **H** to open the debug map and view the road network
- **Configuration**: Cloth Config API settings screen (built-in since 2.0.2), accessible from the world creation screen or by pressing **H** in-game and clicking the top-right corner

## 💡Inspiration

Based on [Countered's Settlement Roads](https://modrinth.com/mod/countereds-settlement-roads), map inspired by [RoadArchitect](https://github.com/FranckRJ/RoadArchitect).

## 🎨Future Plans

- [ ] More roadside decorations?
- [x] Link multiple structure types
- [ ] Link biomes?
- [ ] More beautiful buildings?
- [ ] Road events?
- [x] Custom linking
- [ ] Main road system?
- [x] Slab transitions
- [x] Bezier curve smoothing

## 🚨Notes

>[!NOTE]
> - The more structures configured to locate when loading a world, the longer world creation takes, but the more complete the road network
> - Roads cannot generate on already-loaded chunks, so don't approach road segments before they finish generating
