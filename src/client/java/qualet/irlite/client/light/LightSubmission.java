package qualet.irlite.client.light;

import qualet.irlite.client.light.cookie.CookieArray;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

import org.qualet.irl.light.LightMath;
import org.qualet.irl.light.LightRegistry;

import mchorse.bbs_mod.utils.colors.Color;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Adapts evaluated light values to the existing Core registration contract. */
final class LightSubmission
{
    private LightSubmission()
    {}

    static void emitPoint(PointLightForm form, Matrix4f matrix, double baseX, double baseY, double baseZ,
                                  Vector4f origin)
    {
        matrix.transform(origin.set(0F, 0F, 0F, 1F));

        // origin is the small matrix-local offset; add the double base back to recover
        // the absolute world position without the far-from-origin float quantization.
        Color c = form.color.get();
        LightRegistry.registerPoint(baseX + origin.x, baseY + origin.y, baseZ + origin.z, c.r, c.g, c.b, form.intensity.get(), form.radius.get(), form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
    }

    static void emitSpot(SpotlightForm form, Matrix4f matrix, double baseX, double baseY, double baseZ,
                                 Vector4f origin, Vector4f forward)
    {
        matrix.transform(origin.set(0F, 0F, 0F, 1F));

        // Local +Z = the direction the spotlight points (matches the editor gizmo).
        matrix.transform(forward.set(0F, 0F, 1F, 0F));
        LightMath.normalizeDir(forward.x, forward.y, forward.z, 0F, 0F, 1F, forward);
        float dx = forward.x, dy = forward.y, dz = forward.z;

        LightMath.Cone cone = LightMath.cone(form.radius.get(), form.innerRadius.get());
        float cosOuter = cone.cosOuter();
        float cosInner = cone.cosInner();

        // Resolve the gobo texture (BBS Link) to its texture-array layer (loads on
        // first use, cached after); -1 = no mask, so the cookie is OFF unless a
        // texture is picked. Rotation is stored in degrees on the form (UI/keyframe
        // friendly) -> radians for the SSBO. Cheap per-frame: a link->layer lookup.
        int cookieLayer = CookieArray.resolve(form.cookie.get());
        float cookieRot = (float) Math.toRadians(form.cookieRotation.get());
        float cookieFlags = form.cookieInvert.get() ? 1F : 0F;

        // Direction (w=0) is translation-invariant; only the origin gets the double base
        // added back to recover the absolute world position without float quantization.
        Color c = form.color.get();
        LightRegistry.registerSpot(baseX + origin.x, baseY + origin.y, baseZ + origin.z, dx, dy, dz, c.r, c.g, c.b, form.intensity.get(), form.range.get(), cosOuter, cosInner, form.entitiesOnly.get(), form.blocksOnly.get(), form.anisotropy.get(), form.vlDensity.get(), form.beamStrength.get(), form.bulbSize.get(), form.shadows.get(), (float) cookieLayer, cookieRot, form.cookieScale.get(), cookieFlags, System.identityHashCode(form));
        LightEffectsRegistration.apply(form);
    }
}
