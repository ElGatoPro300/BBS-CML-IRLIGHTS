# Modern Minecraft ports for IRLights

Status: all five modern ports implemented; final build matrix, focused native checks and packaging verification passed.

## Scope and existing approval

Extend the approved shared-source architecture to Minecraft 1.21.4, 1.21.11, 26.1, 26.2 and 26.3. Keep one canonical feature implementation and select audited Minecraft/Iris boundaries at build time. Preserve the three supported older targets, saved form IDs, replay selection serialization, lighting math, patch files and Core as a separately consumed dependency. Do not modify the CML repository for addon build configuration.

## Verified starting state

The addon master is 8756416 with a clean working tree before this planning document. Its target manifest lists the five modern versions as pending. Local lib folders contain CML production JARs for all five, but no corresponding Core JARs. CML metadata and source revision must be checked before accepting those libraries as the newly synchronized host.

The separate irl-core repository has existing branches 1.21.4 (f19e892) and 1.21.11 (37c29e2). These declare Java 21, Yarn and Iris 1.8.8 / 1.10.7 respectively; branch names and coordinates alone do not prove clean builds or runtime compatibility. No local 26.x Core branches exist. Core master is the Java 21 / Minecraft 1.21.1 line. Its shadow orchestration is Minecraft-dependent, so a renamed old JAR cannot serve newer versions.

The addon build currently always requests Yarn and requires remapped intermediary dependencies. Minecraft 26.x uses official names in CML; its dependency namespace, Loom setup, Core artifact handling and Java 25 toolchain need an explicit build path. Preserve strict target/version checking while accepting the actual namespace of each engine family. Never disable library verification to make a build pass.

## Impact and reuse

Affected subsystems are target selection and packaging, Minecraft frame callbacks, render state and geometry submission, CML form/caster/UI integration, Iris matrices/blending/samplers and native verification tools. These are adapters. Domain selection, ownership and guide math remain pure Java.

Extend gradle/minecraft-targets.json, minecraft-targets.gradle and local-libraries.gradle and the addon build only as required by the target matrix. Extend the existing RenderApi and platform source selection instead of creating a second lighting implementation. The current common rendering classes still call BufferRenderer, GameRenderer shader getters and legacy RenderSystem methods, so adding target strings is insufficient. Actual differences in those consumers must move behind focused existing platform boundaries.

Audit AbstractLightFormRenderer, LightGuideRenderer, LightFramePipeline, BbsSoftTransparency and caster submission alongside IrliteClient registration and mixin descriptors. Modern mapping changes can affect all engine-facing adapters; choose isolated mapping projects or source transformation only after checking actual signatures. Avoid duplicate feature trees and reflection-based version guessing.

Use CML's existing render scopes, native geometry and shader adapters where available. Retain the GL/UBO boundary for 1.21.11, reverse-Z depth for 26.2+, and RenderPearl/uniform blocks for 26.3. Do not assume one Iris mixin matches every pipeline family. Required hooks must remain required.

## Ordered implementation

1. Build and inspect the existing Core 1.21.4 branch in an isolated checkout, resolving actual compatibility failures. Verify the matching CML artifact, then implement and verify addon 1.21.4.
2. Repeat for Core/addon 1.21.11, adapting modern render pipelines and native GUI submission.
3. Port the necessary Core engine boundaries to 26.1 with Java 25 and official names, then extend the addon build and adapters. Keep Core's public light/shadow contracts stable where possible. Document any necessary API change before implementation.
4. Extend 26.2 with reverse-Z and its native geometry APIs, then 26.3 with RenderPearl and native uniform blocks.
5. Rebuild the older three addon targets and verify exact Core embedding, class versions, required mixins and patch resource bytes.

Do not label a target supported until dependencies, build, native startup and applicable functional checks pass. Record unavailable dependency combinations explicitly. Do not create release commits or push unless requested.

## Verification

Run existing domain, replay UI/Outline, soft-transparency GPU and refactor lifecycle/caster checks, adapting fixture APIs without relaxing contracts. Exercise shaders off/on, no-shading surfaces, replay ID attachment blending, target/state restoration, world cleanup and required hooks. Run native checks against the exact target library set and toolchain. Compile/remap and packaging success alone do not establish visual acceptance.

Run organize_imports.py on changed Java and organize_lang.py with parity checking for localization changes. Reuse all supported translations; no new UI text is planned. Keep logs, generated fixtures and dependency artifacts under ignored output/lib paths.

## Scope approval

The user previously approved a shared addon base with Core retained as a separate dependency. Completing all five new ports also requires work in the separate Core repository, especially the absent 26.x ports. The user approved including the necessary Core ports while keeping it a separate dependency.

## 1.21.4 verified

Existing Core branch builds cleanly in irl-core-ports/1.21.4; copied its production artifact into lib/1.21.4. Addon build/remap and native startup pass. Outline/replay panels pass; patched Complementary soft/no-shading RGB alpha and exact replay IDs pass; the refactor fixture passes 3,020 bounds/data/lifecycle checks. Domain checks pass 20,022 assertions. Iris 1.21.4 uses ExtendedShader.bind, so blend-hook source selection is independent of the captured-matrix adapter. Shader selection, projection snapshot and entity-render-state differences remain in RenderApi. No user project was opened by these fixtures. Full scene acceptance remains distinct from these focused GPU/native checks.

## Modern caster bridge contract

The 1.21.11 Core branch replaces ImmediateOccluderBatch with RawOccluderBatch; the addon cannot cast the new batch to the removed type. CML already owns immediate native form/entity command dispatch. Extend ShadowRenderer with currentViewMatrix() and currentProjectionMatrix(), each returning a defensive Matrix4f copy of the active pass. The modern CML caster adapter scopes target/projection/model-view, suspends Iris world overrides, renders actual CML forms/entities into the active depth-only target, and restores state. Core retains atlas setup, block triangles, origin snapping, pass cleanup and error isolation. This adapter submits depth directly rather than appending position-only approximations. Verify actual depth pixels and target restoration before considering the target supported.

## 1.21.11 verified

Core 1.1.8 is built on ports/1.21.11 in irl-core-ports/1.21.11. It adds defensive pass matrix accessors and synchronizes atlas framebuffer binds/deletes with the engine cache. Addon build/remap, required mixin startup and native Outline/replay UI pass. Core atlas depth checks measure 3,025 real model pixels, verify shadow movement and nested part transforms, and reject recovered caster failures/GL errors. Soft/no-shading blend checks pass with the native Iris setup-state hook, GLSL Transformer 3 and patched Complementary. The Iris profiler hooks the actual indexed render-pass draw. CML owns modern native form/entity submission; shared caster placement and domain logic remain canonical. Local Core Ivy coordinates include content identity so rebuilding a local JAR cannot silently resolve stale bytes.

## Official namespace bridge

The inspected CML 26.x JARs use the official namespace; 1.21.11 uses intermediary/Yarn. The 26.1 native API also changed model material dispatch, so class renaming alone cannot be its rendering adapter. Keep canonical addon features in their current sources. An isolated gradle/source-bridge Loom project resolves the audited 1.21.11 dependencies and uses Mercury/Lorenz with exact Yarn-to-Mojang mappings for Java symbol and mixin annotation remapping. Generated common sources belong to ignored build output. The 26.x root build consumes those sources plus native platform adapters. Actual behavioral differences remain explicit in the adapters; no reflection-based runtime version probing or copied feature trees. Pin the mapping input version and verify the Mojang mapping checksum. This permits a single build command per target while retaining a single maintained feature implementation.

## 26.x implemented boundaries

Core 1.1.8 builds for each 26.x target with Java 25 and official names. Its block-model adapter reads actual native model parts, material layers, vertex positions and UV pairs. Core remains independent of CML. On 26.2 and 26.3, each shadow pass scopes classic GL clip depth and restores the engine's clip mode and depth-clear value. Atlas values and all seven shader patches retain their existing classic-depth contract. A required adapter hook scopes CML layer depth comparisons to LEQUAL only during a Core bake; ordinary reverse-Z world rendering keeps its native convention.

The shared modern caster implementation is maintained once in casterModern. The source bridge translates it alongside canonical features for 26.x. Minecraft adapters handle camera/screen lookup, buffers, profiler HUD, projection/color snapshots and actual native draw submission. The 26.3 frame hook consumes extracted world partial ticks; its required renderLevel anchor has no DeltaTracker argument. Native RenderPearl types and BBS uniform blocks remain in mc263 and the corresponding Iris/depth adapters. Picking draws a filled volume with exact object-ID RGB values and restores modulation and GL state. Textured icon checks sample actual pixels rather than relying on successful shader compilation.

The bridge verifies the Mojang mapping checksum, resolves Java and mixin symbols with Mercury/Lorenz, and writes generated sources under build/source-bridge. It never rewrites canonical feature sources. Building a 26.x target requires the selected target's CML/Core libraries and the audited 1.21.11 CML/Core symbol inputs. libDir is propagated to the isolated bridge. Only the selected Core is nested in the produced addon.

Native checks now cover the center of the filled picking volume, textured icon colors, actual model and block depth, translation, nested form transforms, defensive matrix copies, clip-depth/clear restoration and recovered caster failures. For 26.2/26.3 the fixture explicitly starts with zero-to-one clip depth and a reverse-Z clear value, so the classic atlas check cannot pass merely because the title screen happened to use a legacy clip range. Full world scene acceptance, all shaderpack combinations and performance benchmarking remain separate manual work.

## Review locations

Core worktrees are outside build output, in the sibling irl-core-ports directory, so a normal Gradle clean cannot remove port source changes. The 1.21.11, 26.1, 26.2 and 26.3 Core ports are maintained on ports/<version>. The 1.21.4 worktree is the existing f19e892 source without port edits. The original Core master checkout remains unchanged. Addon edits are on its existing master checkout. Port changes are committed separately in Core and the addon; no pushes or releases are performed.

## Final recorded verification

All eight addon targets build: 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.11, 26.1, 26.2 and 26.3. Their bundles pass exact Core byte matching, metadata, bytecode, required mixin classes, absence of native test fixtures, and unchanged bytes for all seven patch resources.

The final native UI checks pass on all five modern targets. Native picking, sampled icon colors, model/block caster pixels and pass restoration pass on 1.21.11 and all three 26.x versions. Exact plane depth is checked against Core's projection; the 26.2/26.3 checks start explicitly with zero-to-one clip depth and reverse-Z clear state. Final soft/no-shading alpha, exact replay IDs and shadow isolation pass on 1.20.4, 1.21.4 and 26.3, with additional earlier passes on 1.21.11, 26.1 and 26.2. The legacy native refactor check passes 3,020 checks on 1.20.4; the earlier domain run passed 20,022 assertions. Import checks and 22-locale/134-key parity pass without translation changes.

Reproducible focused runners are tools/verify-replay-ui.py, tools/verify-modern-casters.py, tools/verify-soft-transparency.py, tools/verify-addon-refactor.py and tools/verify-target-bundles.py. The final local execution report is build/final-ports.json; native logs are under build/targets/<version>/<check>-check/client.log. These are focused compatibility checks, not complete world-scene or performance acceptance. No user project was opened by the fixtures.

## Aggregate build

Run `./gradlew buildAll` with JDK 25 to build every manifest target whose status is available. The coordinator loads no Minecraft/Loom project itself, uses a separate coordinator cache, and invokes each actual target sequentially with its own project cache. It stops on any build failure and validates all packaged artifacts with verify-target-bundles.py before reporting success. Local libDir is forwarded; target outputs remain in build/targets/<version>/libs. The aggregate command completed all eight targets and bundle checks locally. Ordinary build retains its single-target behavior.
