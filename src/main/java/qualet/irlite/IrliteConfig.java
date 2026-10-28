package qualet.irlite;

import qualet.irlite.config.ConfigValues;

public final class IrliteConfig
{
    private static final ConfigValues VALUES = new ConfigValues();

    public static ConfigValues values()
    {
        return VALUES;
    }


    private IrliteConfig()
    {}

    public static boolean showGuides()
    {
        return VALUES.showGuides != null && VALUES.showGuides.get();
    }

    /** Shadow maps are only re-baked when the scene changes. Always on — the
     *  cache is transparent (a stale map is a bug to fix, not a mode to pick),
     *  so it lost its BBS toggle. */
    public static boolean shadowCache()
    {
        return true;
    }

    /** When on, world blocks cast shadows by their real shape, and cutout
     *  blocks skip transparent texels (default on). */
    public static boolean shadowBlocks()
    {
        return VALUES.shadowBlocks == null || VALUES.shadowBlocks.get();
    }

    /** Shadow resolution preset ordinal (0 LOW .. 3 ULTRA), default 1 (MEDIUM). */
    public static int shadowQuality()
    {
        return VALUES.shadowQuality != null ? VALUES.shadowQuality.get() : 1;
    }

    /** Block-shadow collection radius in blocks. World blocks farther than this
     *  from a light cast no shadow even when the light's range is larger — it
     *  bounds the per-light bbox walk. Fixed at 24: no BBS knob. */
    public static int shadowBlockRadius()
    {
        return 24;
    }

    /** Max full static shadow bakes started per frame before the rest are
     *  deferred to a later frame (default 4). Spreads a mass invalidation (a
     *  block edit near a cluster of lamps) across frames instead of one spike;
     *  the deferred lamps keep their existing (slightly stale) map until baked.
     *  &lt;= 0 disables throttling (bake everything every frame). First bakes and
     *  tile-reassign bakes are never deferred (they would sample a blank or
     *  foreign map); dynamic overlays and static-&gt;live copies are never
     *  budgeted (they must run every frame). Fixed at 4: no BBS knob. */
    public static int shadowBakeBudget()
    {
        return 4;
    }

    /** Pose/oversize slack of the partial-tile shadow rect (both axes), as a
     *  fraction of the caster's half-height. Covers wide animation poses (arms
     *  out) and forms drawn bigger than their hitbox; oversized bounds only cost
     *  bake speed and fall back to the full-tile path, which never clips.
     *  Fixed at 1.0 — the value calibrated against the visual gate. */
    public static float shadowPoseReach()
    {
        return 1.0F;
    }

    /** Partial-tile spot overlay: on a steady overlay frame only the moving
     *  subject's projected rect is copied, redrawn and refiltered instead of the
     *  whole tile. The rect is built from caster HITBOXES (+ the pose slack), so
     *  a form drawn far past its hitbox can clip at the rect edge, moving with
     *  the subject — switch OFF to trade that risk for the full-tile per-frame
     *  cost. Default on. */
    public static boolean shadowPartialTile()
    {
        return VALUES.shadowPartialTile == null || VALUES.shadowPartialTile.get();
    }

    /** Max lights uploaded to the shader SSBO per frame; the injected shader loops
     *  over every uploaded light per fragment, so fewer = cheaper. When more lights
     *  are in range than this, the nearest (highest-priority) ones win; the rest are
     *  skipped for lighting but stay registered and keep casting/receiving shadows.
     *  0 disables the cap (upload everything), which is the default — and no
     *  quality preset touches this knob, so the cap stays wherever it is put. */
    public static int maxShaderLights()
    {
        return VALUES.maxShaderLights != null ? VALUES.maxShaderLights.get() : 0;
    }

    /** Global multiplier on IRLite volumetric light brightness, applied live
     *  through the SSBO header each frame — no shader reload. 1.0 = pack
     *  default, 0 = IRLite volumetrics off. Default 1.0. */
    public static float vlIntensity()
    {
        return VALUES.vlIntensity != null ? VALUES.vlIntensity.get() : 1F;
    }

    /** Runtime toggle for VL shadows, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlShadowsLive()
    {
        return VALUES.vlShadowsLive == null || VALUES.vlShadowsLive.get();
    }

    /** Runtime toggle for VL noise, applied live through the SSBO header
     *  flags each frame — no shader reload. Default on. */
    public static boolean vlNoiseLive()
    {
        return VALUES.vlNoiseLive == null || VALUES.vlNoiseLive.get();
    }

    /** Ray-march steps per light in the VL pass, applied live through the
     *  globals UBO each frame — no shader reload. Default 48 (pack's
     *  IRLITE_VL_STEPS default). */
    public static int vlSteps()
    {
        return VALUES.vlSteps != null ? VALUES.vlSteps.get() : 48;
    }

    /** Maximum VL ray distance in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 96 (pack's
     *  IRLITE_VL_MAX_DIST default). */
    public static float vlMaxDist()
    {
        return VALUES.vlMaxDist != null ? VALUES.vlMaxDist.get() : 96F;
    }

    /** Tap the shadow maps every Nth VL march step, applied live through the
     *  globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_SHADOW_STRIDE default). */
    public static int vlShadowStride()
    {
        return VALUES.vlShadowStride != null ? VALUES.vlShadowStride.get() : 2;
    }

    /** Extra VL glow near the light source itself, applied live through the
     *  globals UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_BOOST default). */
    public static float vlTipBoost()
    {
        return VALUES.vlTipBoost != null ? VALUES.vlTipBoost.get() : 1.5F;
    }

    /** Radius of the tip glow in blocks, applied live through the globals
     *  UBO each frame — no shader reload. Default 1.5 (pack's
     *  IRLITE_VL_TIP_RADIUS default). */
    public static float vlTipRadius()
    {
        return VALUES.vlTipRadius != null ? VALUES.vlTipRadius.get() : 1.5F;
    }

    /** How strongly the noise modulates the VL beam, applied live through the
     *  globals UBO each frame — no shader reload. Default 0.6 (pack's
     *  IRLITE_VL_NOISE_AMOUNT default). */
    public static float vlNoiseAmount()
    {
        return VALUES.vlNoiseAmount != null ? VALUES.vlNoiseAmount.get() : 0.6F;
    }

    /** Approximate size of the VL noise puffs in blocks, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_SCALE default). */
    public static float vlNoiseScale()
    {
        return VALUES.vlNoiseScale != null ? VALUES.vlNoiseScale.get() : 2F;
    }

    /** How fast the VL noise puffs drift, applied live through the globals
     *  UBO each frame — no shader reload. The core quantizes it to 0.25 steps
     *  (whole field-periods per wind wrap cycle) so the fog never pops when
     *  the shader's time counter wraps. Default 0.25 (pack's
     *  IRLITE_VL_NOISE_SPEED default). */
    public static float vlNoiseSpeed()
    {
        return VALUES.vlNoiseSpeed != null ? VALUES.vlNoiseSpeed.get() : 0.25F;
    }

    /** How fast the VL noise puffs reshape (morph) on top of the drift,
     *  applied live through the globals UBO each frame — no shader reload.
     *  0 = classic drifting-only fog. The core quantizes it to 0.25 steps so
     *  the morph crossfade phase stays slice-congruent when the shader's time
     *  counter wraps. Default 0 = morph off (runtime-only knob, no pack define);
     *  no quality preset touches it. */
    public static float vlNoiseMorph()
    {
        return VALUES.vlNoiseMorph != null ? VALUES.vlNoiseMorph.get() : 0F;
    }

    /** Sample the VL density noise every Nth march step, applied live through
     *  the globals UBO each frame — no shader reload. Default 2 (pack's
     *  IRLITE_VL_NOISE_STRIDE default). */
    public static int vlNoiseStride()
    {
        return VALUES.vlNoiseStride != null ? VALUES.vlNoiseStride.get() : 2;
    }

    /** Runtime toggle for the blue-noise VL march dither, applied live through
     *  the globals UBO flags each frame — no shader reload. On replaces the
     *  pack's hash dither with the mod's blue-noise texture (banding pushed
     *  into fine grain the eye discards); off falls back to the pack's own
     *  dither. Default on. */
    public static boolean vlBlueNoise()
    {
        return VALUES.vlBlueNoise == null || VALUES.vlBlueNoise.get();
    }

    /** Runtime toggle for per-frame rotation of the VL blue-noise dither,
     *  applied live through the globals UBO flags each frame — no shader
     *  reload. Default on; switch it off for a shot that shimmers/boils on
     *  moving lamps without temporal anti-aliasing. */
    public static boolean vlDitherTemporal()
    {
        return VALUES.vlDitherTemporal == null || VALUES.vlDitherTemporal.get();
    }

    /** Runtime toggle for cluster culling in the VL march, applied live through
     *  the globals UBO flags each frame — no shader reload. On reuses the
     *  per-frame screen-tile light grid so each pixel's volumetric march skips
     *  lights whose on-screen bounds miss its tile — the image is identical,
     *  only the cost drops. Default on. */
    public static boolean vlClusterCull()
    {
        return VALUES.vlClusterCull == null || VALUES.vlClusterCull.get();
    }

    /** Runtime toggle for the VL shadow Hi-Z segment skip, applied live
     *  through the globals UBO flags each frame — no shader reload. On
     *  classifies each pixel's march segment once against the coarse min/max
     *  spot shadow pyramid: segments provably fully lit skip every per-step
     *  shadow-map tap, segments provably fully occluded skip the light
     *  entirely; anything ambiguous falls back to the per-step taps. Spot
     *  lights only — the image is identical, only the cost drops. Default
     *  on. */
    public static boolean vlShadowHiz()
    {
        return VALUES.vlShadowHiz == null || VALUES.vlShadowHiz.get();
    }

    /* ---- outline (wave 1: moved off the Iris screen into the globals UBO) ----
     *
     * All ten apply live through the UBO each frame — no shader reload. They only
     * do anything on a pack patched with the runtime globals block; on the other
     * six packs the UBO is absent and these silently do nothing, the same way the
     * VL knobs already behave. Defaults mirror the pack's compile-time defines,
     * so a fresh install looks identical to the pre-migration build. */

    /** Master toggle for the light-driven rim VALUES.outline. Default on. */
    public static boolean outline()
    {
        return VALUES.outline == null || VALUES.outline.get();
    }

    /** What the VALUES.outline is drawn on: 0 all, 1 entities only, 2 blocks only.
     *  Default 1 (pack's IRLITE_OUTLINE_TARGET). */
    public static int outlineTarget()
    {
        return VALUES.outlineTarget != null ? VALUES.outlineTarget.get() : 1;
    }

    /** Overall rim brightness multiplier. Default 0.65. */
    public static float outlineStrength()
    {
        return VALUES.outlineStrength != null ? VALUES.outlineStrength.get() : 0.65F;
    }

    /** Depth-edge detector tap offset in pixels — larger = thicker, coarser
     *  silhouette. Default 6 (the slider maximum): picked by eye 2026-07-21. */
    public static int outlinePixelSize()
    {
        return VALUES.outlinePixelSize != null ? VALUES.outlinePixelSize.get() : 6;
    }

    /** Fresnel falloff exponent; higher = the rim hugs grazing angles more
     *  tightly. Default 2.2. */
    public static float outlineFresnelPower()
    {
        return VALUES.outlineFresnelPower != null ? VALUES.outlineFresnelPower.get() : 2.2F;
    }

    /** Base rim strength on surfaces facing AWAY from the light (backlight rim).
     *  0 = off; it is slider-only by the pack's own idiom, no companion toggle.
     *  Default 1.0. */
    public static float outlineBack()
    {
        return VALUES.outlineBack != null ? VALUES.outlineBack.get() : 1F;
    }

    /** Toggle for the catch-light rim on surfaces facing TOWARD the light.
     *  Default off. Kept as its own toggle rather than folded into
     *  VALUES.outlineFrontStrength == 0 so switching it off preserves the slider
     *  value to come back to. */
    public static boolean outlineFront()
    {
        return VALUES.outlineFront != null && VALUES.outlineFront.get();
    }

    /** Strength of the front catch-light rim. Default 0.3. */
    public static float outlineFrontStrength()
    {
        return VALUES.outlineFrontStrength != null ? VALUES.outlineFrontStrength.get() : 0.3F;
    }

    /** Toggle for the soft inner Fresnel halo that feeds the pack's bloom.
     *  Default off. Same rationale as VALUES.outlineFront for keeping the toggle. */
    public static boolean outlineGlow()
    {
        return VALUES.outlineGlow != null && VALUES.outlineGlow.get();
    }

    /** Strength of the inner glow halo. Default 0.12. */
    public static float outlineGlowStrength()
    {
        return VALUES.outlineGlowStrength != null ? VALUES.outlineGlowStrength.get() : 0.12F;
    }

    /* ---- shadows, live half (wave 3) ---------------------------------------- */

    /** Everyday on/off for shadows cast by IRLights lights, applied live through
     *  the globals UBO each frame — no shader reload. This is NOT the same as the
     *  pack's IRLITE_SHADOWS define, which stays on the Iris screen purely as a
     *  compile-time escape hatch for drivers that reject samplerCubeArray: with
     *  that one off there is no shadow code left for this toggle to gate.
     *  Default on. */
    public static boolean shadowsLive()
    {
        return VALUES.shadowsLive == null || VALUES.shadowsLive.get();
    }

    /** Apparent size of the light source, which sets how quickly the shadow edge
     *  spreads with distance from the caster (PCSS penumbra width). 0 = hard
     *  edges. A light with its own bulb size set still overrides this. Applied
     *  live through the globals UBO each frame — no shader reload. Default 0.10
     *  (pack's IRLITE_SHADOW_SIZE default). */
    public static float shadowSoftness()
    {
        return VALUES.shadowSoftness != null ? VALUES.shadowSoftness.get() : 0.10F;
    }

    /* ---- surface lighting (wave 2: moved off the Iris screen into the globals UBO) ----
     *
     * Same deal as the VALUES.outline block: live through the UBO each frame, silently
     * inert on a pack without the surface block, defaults equal to the pack's
     * former compile-time defaults. */

    /** Diffuse light from IRLights lights on surfaces. Default on. */
    public static boolean diffuse()
    {
        return VALUES.diffuse == null || VALUES.diffuse.get();
    }

    /** Master multiplier for VALUES.diffuse, VALUES.specular and the VALUES.outline rim; the
     *  volumetric beams keep their own VALUES.vlIntensity. Default 1.0. */
    public static float intensity()
    {
        return VALUES.intensity != null ? VALUES.intensity.get() : 1F;
    }

    /** Specular highlight from IRLights lights. Default on. */
    public static boolean specular()
    {
        return VALUES.specular == null || VALUES.specular.get();
    }

    /** Extra multiplier on the VALUES.specular highlight only. Default 1.0. */
    public static float specularIntensity()
    {
        return VALUES.specularIntensity != null ? VALUES.specularIntensity.get() : 1F;
    }

    /** Toon (cel) banding of the VALUES.diffuse light. Default off. */
    public static boolean toon()
    {
        return VALUES.toon != null && VALUES.toon.get();
    }

    /** Number of VALUES.toon brightness bands. Default 3. */
    public static int toonBands()
    {
        return VALUES.toonBands != null ? VALUES.toonBands.get() : 3;
    }

    /** Softness of the VALUES.toon band edges; 0 = hard steps. Default 0.10. */
    public static float toonSmooth()
    {
        return VALUES.toonSmooth != null ? VALUES.toonSmooth.get() : 0.10F;
    }
}
