# AP Physics C Final — Interactive Physics Simulator

A Java Swing desktop app with two live, interactive physics simulations, built as an AP Physics C final project.

## Simulations

### Projectile Motion
- Adjustable launch angle, initial speed, gravity, and optional quadratic air resistance
- Euler integration at ~62.5 fps
- Live trail rendering, real-time HUD (position, velocity, max height, range), and an on-canvas scale bar

### Three-Body Gravitational System
- RK4 integration of Newtonian gravity, with softening to avoid singularities at close encounters
- Six presets: Sun-Earth-Moon, Figure-8 orbit, Binary Star + Planet, Lagrange L4/L5, Slingshot, and a randomized N-body mode
- Adjustable simulation speed ("time warp"), live energy-conservation tracking, optional body merging on collision, and CSV export of trajectory data

## Files

| File | Description |
|---|---|
| `PhysicsDemo.java` | Main application — both simulation tabs, UI, and rendering |
| `Body.java` | Physical body representation used by the gravity simulation |
| `Vector2D.java` | 2D vector math supporting both simulations |

## Running it

```bash
javac *.java
java PhysicsDemo
```

## Tech stack

![Java](https://img.shields.io/badge/-Java-007396?style=flat-square&logo=openjdk&logoColor=white)

**Libraries:** Java Swing / AWT (`Graphics2D`) — no external dependencies
