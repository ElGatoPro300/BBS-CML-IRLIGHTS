# CML soft transparency compatibility

IRLights adds an unblended replay-ID attachment to Iris programs. Iris locks the global blend state while such an attachment override is active. CML's deferred soft draw enables alpha blending through RenderSystem, but that request can be deferred by the lock: the surface remains opaque even though the vertex alpha changes.

The adapter reasserts alpha blending on draw buffer zero only, immediately before CML executes the queued callback and after an Iris entity shader reapplies its overrides. Replay-ID and other auxiliary attachments retain their own blend policies. Draws outside the CML soft/no-shading flush are unchanged.

Deferred callbacks also retain their submitting replay ID and restore the previous ID in a finally block. IRLights shadow-bake geometry stays synchronous and cannot enter either color queue.

## Verification

```powershell
python tools/verify-soft-transparency.py --shaderpack <IRLights-patched-Complementary-folder-or-zip>
```

The opt-in fixture runs in an isolated Minecraft client. It uses CML's actual queues and entry runner, Iris's indexed blend lock and the actual ExtendedShader attachment overrides compiled from the supplied shaderpack. A diagnostic MRT shader checks the exact RGB blend and replay ID numerically. It does not substitute a screenshot comparison for those checks.

The pre-fix fixture reproduced fully opaque RGB at fractional alpha, replay ID zero and shadow casters admitted to the color queues. The fixed checks cover both queues, eight alpha values (including 0.01 and 0.998), patched/unpatched blend-lock scenarios, exception restoration, untagged callbacks, unchanged opaque draws and shadow exclusion. Validation target: Fabric 1.21.1, CML 3.0 beta 4, Iris 1.8.8 and patched Complementary Reimagined r5.9.3. Full in-world visual acceptance remains separate from this focused GPU regression.
