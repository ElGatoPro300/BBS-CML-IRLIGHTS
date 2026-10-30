<div align="center">

# ✦ IRLights

**Dynamic point & spotlight lighting for Minecraft 1.20.1 · 1.20.4 · 1.21.1**

*A [BBS](https://github.com/mchorse/bbs) addon that brings real-time shadows, volumetric light shafts, and per-light specular to Iris shaderpacks — no shaderpack edits required.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.20.4%20%7C%201.21.1-62b47a?style=flat-square&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loom-dbb967?style=flat-square)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-MIT-3da639?style=flat-square)](LICENSE)

</div>

---

## What is IRLights?

IRLights is a client-side Fabric mod that extends [BBS](https://github.com/mchorse/bbs) with a full dynamic lighting system. You place **point lights** and **spotlights** in your scene from the BBS editor, and IRLights does the rest:

- bakes per-light shadow maps every frame
- uploads all light data to the GPU via a single SSBO
- injects the matching GLSL into your shaderpack via a one-click patcher

The result is physically-plausible diffuse + specular lighting, hard/soft shadows, and volumetric fog shafts — all driven by BBS animation keyframes.

---

## Features

| Feature | Details |
|---|---|
| **Point lights** | Omnidirectional, cube-map shadows, radius + falloff |
| **Spotlights** | Cone angle, penumbra, atlas shadows |
| **Volumetrics** | Per-light ray-marched shafts with shadow occlusion |
| **Specular** | GGX BRDF, per-light roughness + intensity |
| **Shadow quality** | Four presets — Low / Medium / High / Ultra |
| **SSBO pipeline** | std430 binding 7, up to 2048 lights per frame |
| **Patcher** | Injects GLSL into any supported shaderpack in one click |
| **BBS UI** | Light forms in the BBS editor, live preview |

---

## Supported Shaderpacks

IRLights ships ready-to-use `.irlights` patch files for all packs below.  
Apply them once with the in-game patcher — no manual GLSL edits needed.

| Shaderpack | Author | Patch file |
|---|---|---|
| [Photon](https://modrinth.com/shader/photon-shader) | SixthSurge | `patches/photon.irlights` |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | EminGT | `patches/complementaryreimagined.irlights` |
| [BSL Shaders](https://modrinth.com/shader/bsl-shaders) | CaptTatsu | `patches/bsl.irlights` |
| [Solas](https://modrinth.com/shader/solas-shader) | Septonious | `patches/solas.irlights` |
| [Bliss](https://modrinth.com/shader/bliss-shader) | X0nk | `patches/bliss.irlights` |
| [Rethinking Voxels](https://modrinth.com/shader/rethinking-voxels) | gri573 | `patches/rethinkingvoxels.irlights` |
| IterationRP&nbsp;† | Tahnass | `patches/iterationrp.irlights` |

<sub>† **IterationRP** is a paid shaderpack — buy it from the author. Our `.irlights` patch is free and public, but it only applies on top of a copy you already own.</sub>

---

## How It Works

IRLights has three layers that work together:

```
┌─────────────────────────────────────────────────────┐
│  BBS Editor  →  PointLightForm / SpotlightForm       │  you place lights here
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  Java Addon  →  LightCollector → ShadowBaker         │  bakes maps, fills SSBO
│               → LightBuffer (SSBO binding 7)         │
│               → Iris sampler injection               │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  Patcher     →  .irlights patches                    │  injects GLSL once
│               → PatchEngine (anchor-based edits)     │
│               → validate / apply / revert UI         │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  GLSL Inject →  irlite_lights.glsl                   │  runs on GPU every frame
│               → diffuse · specular · shadows · VL    │
└─────────────────────────────────────────────────────┘
```

---

## Installation

> **Requirements:** Minecraft 1.20.1 / 1.20.4 · Fabric Loader ≥ 0.19 · Iris ≥ 1.7 · BBS (latest)
>
> Jars are per-version — grab the one matching your instance: `irlite-1.1+mc1.20.1.jar` or `irlite-1.1+mc1.20.4.jar`.

1. Drop `irlite-*.jar` into your `mods/` folder alongside BBS and Iris.
2. Launch the game once to generate config.
3. Open the **IRLights** category in BBS settings and pick your shaderpack.
4. Click **Apply Patch** — the patcher injects the GLSL automatically.
5. Enable the patched shaderpack in Iris and reload shaders.

---

## Usage

1. Enter a BBS scene and open the **Lights** panel in the editor.
2. Add a **Point Light** or **Spotlight** and adjust radius, color, intensity, and shadow quality.
3. Keyframe any light property for animated sequences.
4. Reload shaders (`F3 + R`) to see the result in real time.

---

## Building from Source

The shared source tree selects Minecraft/Iris adapters for 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.11, 26.1, 26.2 and 26.3. Put matching production CML and IRL Core libraries in `lib/<version>/`; see [local libraries](lib/README.md). Core is embedded once, while CML remains an external required mod.

```bash
./gradlew build -Pmc=1.20.4
# output: build/libs/bbs-cml-irlights-<version>-1.20.4.jar
./gradlew build -Pmc=1.20.1
./gradlew build -Pmc=1.21.1
./gradlew runClient -Pmc=1.20.4 -PrunDir=build/manual-1.20.4
```

Target dependencies and verification status live in `gradle/minecraft-targets.json`. Java 17 runs 1.20.x, Java 21 runs 1.21.x, and Java 25 runs 26.x. Run Gradle with Java 25 when building 26.x. For example: `./gradlew build -Pmc=26.3`. The official-name source bridge also requires the CML/Core libraries in `lib/1.21.11`; actual 26.x rendering uses its selected native adapter. See [architecture and verification status](docs/addon-refactor-status.md) before adding a target.

---

## Releasing

Cutting a release is one command:

```powershell
pwsh -File tools/release.ps1 -Version 1.1.2
```

It bumps `mod_version`, builds the `1.20.1` product jar, then creates the `v1.1.2`
tag and a GitHub Release with the jar attached and notes generated from the commit
log — all in one step. Add `-DryRun` to preview, `-Draft` to review before publishing.

The jar is built **locally** on purpose: the addon links against the third-party BBS
jar, which is not in git and cannot be republished, so it cannot be produced on a
clean CI runner. The [`release.yml`](.github/workflows/release.yml) workflow is only a
safety net — if a `v*` tag is ever pushed by hand, it creates a draft Release with
notes (no jar) so the tag is not lost.

---

## License

Released under the [MIT License](LICENSE) — © 2026 qualet.

The shared lighting & patcher engine lives in [irl-core](https://github.com/quaIett/irl-core)
(also MIT). Third-party shaderpacks referenced in `patches/` remain under the licenses of
their respective authors.

Build all available Minecraft targets with `./gradlew buildAll` (`.\gradlew.bat buildAll` in PowerShell), using JDK 25. This runs targets sequentially with isolated caches, stops on the first failure, then verifies each JAR's exact Core, metadata, bytecode and patch resources. `build` still builds only the selected/default target. All required CML/Core libraries must already be present; `-PlibDir` is supported.

Both `build` and `buildAll` collect installable JARs in `build/libs/`, named with their Minecraft version. Per-target build outputs remain under `build/targets/<version>/`. Source and development JARs are not copied into the common folder.
