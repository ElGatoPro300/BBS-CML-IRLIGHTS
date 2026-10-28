# Shared addon sources and porting status

The addon now selects platform adapters at build time rather than maintaining independent feature implementations per Minecraft branch. The developer approved this direction in the refactor blueprint. No CML or Core source files are modified by this work.

## Build and run

```powershell
./gradlew.bat build -Pmc=1.20.1
./gradlew.bat build -Pmc=1.20.4
./gradlew.bat build -Pmc=1.21.1
./gradlew.bat runClient -Pmc=1.20.4 -PrunDir=build/manual-1.20.4
```

Keep one exact CML and Core production library per target in lib/<version>. Build output, fixture classpaths and native fixture runs are isolated under build/targets/<version>. Target metadata specifies Java, mappings, Fabric, Sodium and Iris versions. Java runtime compatibility is a Gradle project property so Loom preserves the original LWJGL runtime required by Sodium on 1.20.1.

## Implemented boundaries

- src/domain/java: immutable replay selections, frame/world replay ownership and spotlight ray math. The domain fixture compiles against an empty engine classpath.
- src/main/java/qualet/irlite/io: selection JSON encoding with unchanged serialized keys, order, modes and fail-closed behavior. CML Value fields and persistence remain adapters.
- src/platform/mc120 and mc121: buffer submission, model-view stack operations, identifiers and exact GameRenderer injection signatures. GameRenderer callbacks delegate shared frame orchestration to LightFramePipeline.
- src/platform/iris17 and iris18: exact Iris matrix and blending hooks. Hook mismatches remain required mixin failures rather than permissive fallbacks.
- LightCollector: coordinator with separate model-block and film sources, state-balanced form traversal, global parameter uploads and shared light submission.
- IRLiteBbsCasterSource: Core contract coordinator with separate CasterCollector, CasterBounds and CasterGeometry. Candidate order and conservative unknown revisions remain unchanged.
- ClientRuntime: bootstrap bridge to instance-owned replay state, guide drag state, pending guide snapshots, resource revisions, profiling sessions and patcher controller. World/disconnect cleanup is explicit; resource revision numbers survive world changes.
- ProfileSession: owns timer queries, statistics, capture and differential sweep; ProfileHud owns presentation. VlProfiler remains a stateless callback bridge. Optional GPU resources stay in engine adapters.
- PatcherController and PatchOperations: settings controls coordinate operations instead of owning patch execution. Localization is accessed only after CML initialization. The seven existing patch files are packaged as common resources.
- ConfigValues: instance-owned CML Value bindings; stable IrliteConfig read accessor signatures and persisted setting identifiers remain unchanged.

The existing CookieArray already owns its GL/cache data in an instance behind a static callback bridge and remains an adapter to Core. The bootstrap bridge and stable read facades are deliberate integration boundaries; they are not domain state. Existing form/render/editor classes remain CML adapters, not candidates for being declared engine-independent just by moving packages.

## Checks

```powershell
python tools/verify-domain.py
python tools/verify-replay-ui.py --mc 1.20.4
python tools/verify-soft-transparency.py --mc 1.20.4 --shaderpack <patched-Complementary-folder-or-zip>
python tools/verify-addon-refactor.py --mc 1.20.4
python tools/verify-target-bundles.py
python tools/organize_imports.py --check
python tools/organize_lang.py --check --parity
```

The domain regression checks world/instance isolation, stable tokens, deferred callback scope and exceptions, immutable selections and 10,000 deterministic ray samples against the preceding behavior. The native refactor check compares 1,000 randomized conservative caster transforms with the pre-refactor source pinned at 9c93d2a, checks actual CML form save/load and selection modes, and checks lifecycle cleanup and enables/disables the real GL profiler, including timer-query cleanup. Its baseline source is generated into ignored build output and is never shipped.

The soft fixture checks both CML queues, eight alpha values, the real Iris attachment override, exact replay IDs and shadow exclusion. It uses a diagnostic GPU target; it does not claim full in-world scene acceptance. The Outline fixture exercises real point/spot panels and replay picker availability. Guide snapshot checks and light traversal math remain additional focused regressions. Legacy tools/verify-profiles-bbs.ps1 (BBS 2.7 TrackCatalog) and tools/verify-render-cookie.ps1 (older renderer boundary stubs) predate the CML 3 port and are not used as evidence for this refactor. Their current compilation failures are existing API drift, not passing checks. The native checks above exercise the current CML classes.

## Remaining scope and honest compatibility limits

Targets 1.21.4, 1.21.11, 26.1, 26.2 and 26.3 are recorded as pending. Matching Core builds, rendering dependency combinations and audited version hooks are required before marking any of them supported. Mapping/toolchain changes in 26.x may require a separate platform project; adding a version string is insufficient.

Full in-world acceptance still includes moving shadows on nested forms/model blocks, cookies, volumetrics, guide dragging and film keyframes/undo, resource reload, patcher writes and representative supported shaderpacks. Performance equivalence must be measured on a fixed scene before claiming FPS improvement. No user project was opened or modified by the focused fixtures.

The refactor does not introduce new UI text or translation keys. The existing 22 locale files and 134 keys remain unchanged. Legacy hardcoded form-editor labels and diagnostic HUD output remain a localization audit task; key parity is not proof of translation quality. The architecture is in place for incremental ports, not a claim that every CML branch already runs IRLights.

## Recorded focused results

The three existing targets passed build/remap and native Outline, soft/no-shading GPU and caster/data/lifecycle checks from this source tree. The standalone domain check passed 20,022 assertions. The existing light traversal math fixture passed 370,491 checks; guide overlay passed 20 snapshot/API checks on 1.21.1; profiler aggregation/CSV checks passed. Bundle validation checks exact embedded Core bytes, Java class versions, required mixin classes, metadata and all seven patch resource bytes. Machine-readable reports are generated under ignored build/refactor-checks.json and build/refactor-bundles.json.

These results do not close the remaining native scene acceptance and localization audit listed above, and do not promote the five pending targets to supported.
