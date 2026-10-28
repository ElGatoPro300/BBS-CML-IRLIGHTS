package qualet.irlite.client.light;

import qualet.irlite.IrliteConfig;
import qualet.irlite.client.diag.VlProfiler;

import org.qualet.irl.light.LightBuffer;
import org.qualet.irl.light.LightRegistry;
import org.qualet.irl.light.VlGlobalsBuffer;

/** Pushes live configuration to the existing Core buffers before frame collection. */
final class LightGlobals
{
    private LightGlobals()
    {}

    static void apply()
    {
        // Track the "max shader lights" slider each frame: caps how many lights the
        // flush packs into the SSBO (registration + shadow caches still see them all).
        LightRegistry.setUploadCap(IrliteConfig.maxShaderLights());
        // Clustering has no knob: it is always on (core default), the image is
        // identical either way and it only ever makes the per-pixel loop cheaper.
        // For an A/B measurement, start with -Dirlite.noClustering=true.
        // Track the VL intensity slider each frame: lands in the SSBO header on
        // upload, so patched shaders read it live without a recompile.
        LightBuffer.setVlGlobalIntensity(IrliteConfig.vlIntensity());
        // Track the live VL toggles each frame: packed as header bit flags
        // (bit0 = VL shadows, bit1 = VL noise) read by runtime-flag patches.
        LightBuffer.setVlFlags((IrliteConfig.vlShadowsLive() ? 1 : 0) | (IrliteConfig.vlNoiseLive() ? 2 : 0));
        // Track the full VL knob set each frame: lands in the globals UBO
        // (binding 7) on upload, so UBO-era patches read every VL number and
        // flag live without a recompile (bit0 = VL shadows, bit1 = VL noise,
        // bit2 = blue-noise dither, bit3 = temporal dither rotation,
        // bit4 = VL cluster culling, bit5 = Hi-Z skip, bit6 = bilateral
        // upsample). The two header pushes above stay for pre-UBO patches
        // until the fleet is regenerated.
        VlGlobalsBuffer.set(
            IrliteConfig.vlIntensity(),
            IrliteConfig.vlMaxDist(),
            IrliteConfig.vlTipBoost(),
            IrliteConfig.vlTipRadius(),
            IrliteConfig.vlNoiseAmount(),
            IrliteConfig.vlNoiseScale(),
            IrliteConfig.vlNoiseSpeed(),
            IrliteConfig.vlNoiseMorph(),
            IrliteConfig.vlSteps(),
            IrliteConfig.vlShadowStride(),
            IrliteConfig.vlNoiseStride(),
            (IrliteConfig.vlShadowsLive() ? 1 : 0) | (IrliteConfig.vlNoiseLive() ? 2 : 0)
                | (IrliteConfig.vlBlueNoise() ? 4 : 0) | (IrliteConfig.vlDitherTemporal() ? 8 : 0)
                | (IrliteConfig.vlClusterCull() ? 16 : 0) | (IrliteConfig.vlShadowHiz() ? 32 : 0)
                | (LightCollector.VL_BILATERAL ? 64 : 0)
        );
        // Outline knobs ride the same UBO but push separately: the sweep below
        // rebuilds the VL flag word from the VL toggles alone, so folding the
        // outline bits into that argument would let a sweep clear them. The core
        // ORs the two flag words together at upload instead.
        VlGlobalsBuffer.setOutline(
            IrliteConfig.outline(),
            IrliteConfig.outlineTarget(),
            IrliteConfig.outlineStrength(),
            IrliteConfig.outlineFresnelPower(),
            IrliteConfig.outlineBack(),
            IrliteConfig.outlineFront(),
            IrliteConfig.outlineFrontStrength(),
            IrliteConfig.outlineGlow(),
            IrliteConfig.outlineGlowStrength(),
            IrliteConfig.outlinePixelSize()
        );
        VlGlobalsBuffer.setShadow(IrliteConfig.shadowsLive(), IrliteConfig.shadowSoftness());
        VlGlobalsBuffer.setSurface(
            IrliteConfig.diffuse(),
            IrliteConfig.intensity(),
            IrliteConfig.specular(),
            IrliteConfig.specularIntensity(),
            IrliteConfig.toon(),
            IrliteConfig.toonBands(),
            IrliteConfig.toonSmooth()
        );
        // Dev VL profiler sweep (-Dirlite.profileVl=true): may re-issue the push
        // above with per-config flag overrides — last write wins before upload.
        // New VlGlobalsBuffer.set args must be mirrored in VlSweep.overrideVlGlobals.
        // setOutline/setShadow/setSurface are NOT mirrored there by design — the
        // sweep only varies VL, and their flag words are separate from set()'s.
        VlProfiler.overrideVlGlobals();

    }
}
