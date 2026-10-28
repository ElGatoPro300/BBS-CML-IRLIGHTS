# IRLights addon refactor and version adapter blueprint

Status: shared-source architecture approved by the developer; implementation and target verification in progress.
Baseline: addon master, Minecraft 1.21.1, version 1.1.9.

## Goal and scope

Refactor the entire addon so features and algorithms can be maintained independently of Minecraft, CML and Iris API changes. Preserve saved forms, configuration keys, replay selection formats, keyframe behavior and shader output. Keep Core as a separately consumed dependency; a complete Core rewrite is a separate scope. Any required Core API changes must be documented before implementation.

Apply CML 3 engineering rules: impact analysis before code, pure domain logic, thin coordinators, explicit lifecycle ownership, completed extractions, centralized localization, organized imports and incremental verification. Class size is a diagnostic, not a reason to introduce empty abstractions.

## Verified inventory

The initial source review counted 85 Java files and approximately 9,515 lines in the addon. Core has 51 Java files and approximately 12,720 lines and is not included in this addon estimate. Relevant addon hotspots are IRLiteBbsCasterSource (933 lines), VlProfiler (724), LightCollector (565), SpotGuideDrag (490), IrliteConfig (430), UIPatcherSection (362) and ReplayOutlineContext (321). Counts describe the inspected checkout and are not acceptance targets.

CML local branch properties identify targets 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.11, 26.1, 26.2 and 26.3. The addon currently has branches and local dependency folders only for 1.20.1, 1.20.4 and 1.21.1. A CML branch is not evidence that matching Core, Iris, Sodium or patched shaderpacks are available.

## Architecture decision proposed for approval

Keep one canonical copy of engine-independent addon logic and select small platform source sets at build time. Existing release branches remain available during migration; do not change their history, merge automatically or delete branches. Prove this arrangement on the existing three targets before extending to newer versions. Do not implement reflection-based version probing for API differences already known at build time.

Suggested source boundaries:

- domain: immutable selection data, selection codec contract, light parameters, transform calculations, filtering rules and drag calculations. No Minecraft, Fabric, CML UI, Iris or OpenGL imports. Keep representation codecs separate from selection semantics.
- application: collection orchestration, replay ownership lifecycle, guide interaction state and resource lifecycle. Dependencies are narrow ports; engine objects do not leak through domain records.
- adapter/cml: forms and Value persistence, registration events, film lookup, replay enumeration, editor controls, keyframes, model/body-part traversal and caster geometry.
- adapter/render: buffer uploads, surface blend policy, GPU resources, shadow submission, cookies and profiling. Own resource creation, reload and disposal explicitly.
- platform: Minecraft/Fabric callbacks, mappings-sensitive calls and Iris-specific hooks. Separate packages/source sets only where actual API differences require them; do not duplicate every adapter per version.

Start with the existing source layout where possible. Extract common boundaries before moving directories, so structural moves and behavior changes remain reviewable separately.

## Initial contracts and owners

These are proposed contracts, not implemented APIs; exact Java signatures must follow call-site analysis in each wave.

- ReplaySelectionData: immutable film identifier, stable replay identifiers and mode; no Film or Form dependency.
- ReplayCatalog: provides stable replay metadata from the CML adapter. Selection policy consumes metadata rather than widgets or runtime entities.
- LightSceneSource: enumerates light candidates from film and world adapters; common collection code handles selection and transformation.
- FrameLightSnapshot: read-only light data for a frame; the GPU adapter uploads it using the existing Core contract.
- ReplayRenderScope: captures and restores replay ownership for immediate and queued callbacks, including exceptions and untagged draws.
- ClientSession: owns world-dependent tokens, caches and invalidation; initialization wires it to callbacks and clears it on world replacement/disconnect.
- RenderCapabilities: explicitly reports supported shader features. Missing optional capabilities must follow a documented fallback and must not silently claim rendering support.

Avoid a universal service locator. Mixins may need a minimal bridge into an owned client service, but that bridge must not become another mutable global render-state store.

## Implementation waves

### R0: behavior baseline and dependency inventory

Inventory all mixins, Core calls, CML calls and shader patch entrypoints. Record serialized identifiers and current defaults. Run relevant existing regression fixtures and record toolchain, library metadata and known failures. Identify all existing pure math helpers before adding any. Build a target manifest that distinguishes supported, dependency-available and unverified versions.

Exit: reproducible baseline for 1.21.1 and a concrete compatibility inventory. Do not treat compilation as visual acceptance.

### R1: pure selection and interaction logic

Extract selection data and filtering from CML Film/Form access in ReplaySelection and its consumers. Reuse existing Core light math rather than duplicating it. Move guide math out of SpotGuideDrag only where the calculation can accept immutable inputs. Preserve serialized replay selections byte/meaning compatibility and empty/legacy/inherit semantics.

Exit: pure Java regression checks exercise legacy selections, malformed data, missing/reordered replays, body-part rules and drag math; UI behavior remains unchanged.

### R2: explicit runtime ownership

Give replay IDs, active frame maps, drag state, profiler state and patcher UI state explicit owners and reset boundaries. Separate persistent Value configuration from transient runtime state. Remove static state only after migrating every consumer, including mixins and deferred callbacks. Preserve main-thread and GPU-context requirements.

Exit: world switch, disconnect, reload, queue flush and exception tests demonstrate correct restoration and cleanup.

### R3: light collection and caster pipeline

Split LightCollector into world/film enumeration adapters, common transformation/filtering and Core submission. Split IRLiteBbsCasterSource into candidate collection, conservative revision policy, bounds/transform calculations and geometry submission. Preserve traversal order, visibility semantics, coordinates and conservative invalidation.

Exit: equivalent light inputs and caster behavior on representative films, model blocks, nested body parts and moving entities. Unknown revisions continue to invalidate conservatively.

### R4: render and compatibility adapters

Isolate Iris blend/uniform/sampler access, Minecraft render callbacks and GPU resource lifecycle. Reuse existing render entrypoints and Core interfaces. Keep mixins thin and fail clearly for required hook mismatches. Preserve replay-ID attachment blending, deferred ownership, shadow exclusion and no-shading behavior.

Exit: soft-transparency GPU regression and Outline UI regression pass; native scene checks cover shaders enabled/disabled, forward/deferred paths, resource reload and world changes.

### R5: UI, patcher and registration

Make panels coordinate focused collaborators; move patcher operations and selection state out of widget orchestration. Continue native CML registration and layout mechanisms. Audit hardcoded user-facing text and migrate it through localized keys in every supported locale, without renaming persisted identifiers. Review translations rather than inferring correctness from key parity.

Exit: point/spot editor, picker, keyframes, undo, presets, patcher and language switching retain behavior. Imports and locale checks pass.

### R6: prove shared sources on existing targets

Introduce build selection for the extracted common code and small version-specific hooks. This necessarily includes addon Gradle/source-set changes; CML Gradle files remain untouched. Compile and run each target against its exact CML/Core/Iris/Sodium libraries. Keep existing branch hierarchy during the transition and migrate release workflow only after the shared-source arrangement works.

Exit: 1.20.1, 1.20.4 and 1.21.1 compile, package the matching Core exactly once and pass applicable native checks. No copied feature implementations between targets.

### R7: add the remaining CML targets

For 1.21.4, 1.21.11, 26.1, 26.2 and 26.3, inspect their actual mapping, Java, loader, CML, Core and renderer dependencies. Group adapters by demonstrated API compatibility rather than version number alone. Mapping/toolchain transitions may require distinct build projects instead of source-set selection; decide from actual dependency evidence. Never invent dependency coordinates or mark a target supported because a directory exists.

Exit per target: validated dependencies, clean build, loader/mixin startup, functional native checks and documented shader support/limitations.

## Verification and completion

Each significant extraction must compile and preserve behavior before the next one begins. Use the existing tools/verify-* checks when their contracts are still applicable; record limitations of fixtures and avoid implementation-mirroring tests. Add focused pure-domain checks for extracted rules. Run organize_imports.py for changed Java files and organize_lang.py --check --parity for locale validation. Test localized UI in the client when strings change.

Native acceptance includes point/spot lights, nested body parts, world model blocks, filters/outlines, alpha and no shading, shadows, cookies, volumetrics, guides, keyframes/undo, save/reload, patcher/presets, resource reload and world/session replacement. Validate supported shader configurations and shaders off. Measure CPU/GPU regressions on a fixed scene before claiming a performance improvement.

Completion means every addon subsystem has an explicit role and lifecycle, common algorithms have one implementation, known version-sensitive calls are isolated, persisted data remains compatible, and every claimed supported target has recorded verification. Unsupported dependency combinations must remain identified as pending rather than being hidden behind permissive guards.

## Approved decision

The developer approved one shared codebase and build-selected platform adapters. Existing release branches are retained without rewriting their history. Core remains a separate dependency. See addon-refactor-status.md for implemented boundaries, verification and remaining acceptance work.
