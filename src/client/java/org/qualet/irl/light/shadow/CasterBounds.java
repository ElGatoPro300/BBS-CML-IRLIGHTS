package org.qualet.irl.light.shadow;

import qualet.irlite.IrliteConfig;
import qualet.irlite.client.light.BbsSilhouetteBridge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.util.math.BlockPos;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.List;

/** Conservative BBS form-chain bounds, with collection-owned scratch storage. */
final class CasterBounds
{
    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;
    static final float OVERLAP_MARGIN = 0.5F;
    final float[] foldScale = new float[3];
    private final float[] foldTranslate = new float[3];
    private float foldSignY = 1F;
    final float[] innerExt = new float[3];
    final double[] innerCenter = new double[3];

    void emitModelBlock(OccluderSink sink, ModelBlockEntity mbe, Form form, Transform t)
    {
        float hbW = Math.max(0.1F, form.hitboxWidth.get());
        float hbH = Math.max(0.1F, form.hitboxHeight.get());

        // Inner chain first: hitbox half-extents grown by the form-chain fold,
        // expanded by the primary form rotation, offset by the folded translate.
        // Feet at y=0, centered in x/z -> inner box center = (0, ihy, 0) pre-fold.
        Transform ft = foldFormChain(form);
        expandInnerBox(ft, hbW * 0.5F * this.foldScale[0], hbH * 0.5F * this.foldScale[1], hbW * 0.5F * this.foldScale[2]);
        float fex = this.innerExt[0], fey = this.innerExt[1], fez = this.innerExt[2];
        double ficx = this.innerCenter[0], ficy = this.innerCenter[1], ficz = this.innerCenter[2];

        // Outer (block) chain: abs scale for extents, SIGNED scale for the
        // center product (a mirror flip moves the center with the geometry).
        float sx = t == null ? 1F : Math.max(0.001F, Math.abs(t.scale.x));
        float sy = t == null ? 1F : Math.max(0.001F, Math.abs(t.scale.y));
        float sz = t == null ? 1F : Math.max(0.001F, Math.abs(t.scale.z));
        float hx = fex * sx;
        float hy = fey * sy;
        float hz = fez * sz;
        double scx = (t == null ? 1.0 : t.scale.x) * ficx;
        double scy = (t == null ? 1.0 : t.scale.y) * ficy;
        double scz = (t == null ? 1.0 : t.scale.z) * ficz;

        BlockPos pos = mbe.getPos();
        double tx = t == null ? 0 : t.translate.x;
        double ty = t == null ? 0 : t.translate.y;
        double tz = t == null ? 0 : t.translate.z;
        // Pivot = block center + ONE t.translate, matching drawModelBlock (which
        // matches the BBS render since the 2026-08-10 review: translate applied
        // once, by applyTransform). Keep the two in lockstep.
        double pivotX = pos.getX() + 0.5 + tx;
        double pivotY = pos.getY() + ty;
        double pivotZ = pos.getZ() + 0.5 + tz;

        float ehx, ehy, ehz;       // abs-rotation-expanded half-extents (radius source)
        double offX, offY, offZ;   // inner center carried through the pivot rotation: R·(scx,scy,scz)
        if (t == null)
        {
            ehx = hx; ehy = hy; ehz = hz;
            offX = scx; offY = scy; offZ = scz;
        }
        else
        {
            // R = Rz·Ry·Rx (createRotationMatrix), identical to applyTransform's order.
            Matrix3f rot = t.createRotationMatrix();
            // half-extent in world axis i = Σ_j |M[i][j]| · h[j]; JOML Matrix3f is
            // column-major (m<col><row>), so the world-axis-i row is (m0i, m1i, m2i).
            ehx = Math.abs(rot.m00) * hx + Math.abs(rot.m10) * hy + Math.abs(rot.m20) * hz;
            ehy = Math.abs(rot.m01) * hx + Math.abs(rot.m11) * hy + Math.abs(rot.m21) * hz;
            ehz = Math.abs(rot.m02) * hx + Math.abs(rot.m12) * hy + Math.abs(rot.m22) * hz;
            // R·(scx,scy,scz) = colX·scx + colY·scy + colZ·scz (columns of the
            // column-major JOML matrix).
            offX = rot.m00 * scx + rot.m10 * scy + rot.m20 * scz;
            offY = rot.m01 * scx + rot.m11 * scy + rot.m21 * scz;
            offZ = rot.m02 * scx + rot.m12 * scy + rot.m22 * scz;
        }

        double cx = pivotX + offX;
        double cy = pivotY + offY;
        double cz = pivotZ + offZ;
        // Minimal circumscribing radius = half the rotated box's diagonal =
        // |(ehx,ehy,ehz)|, + cull slack. The plain OVERLAP_MARGIN is not enough here:
        // the sphere derives from the form's HITBOX, and the drawn model routinely
        // extends past it, so at a cone/reach boundary the caster culls out ENTIRELY
        // (the whole shadow blinks — the partial-tile rect slack can't help, it only
        // widens the rect of casters that survived the cull). Mirror that rect slack,
        // max(OVERLAP_MARGIN, poseReach·ehy) incl. the sanitizer, on the emitted
        // sphere: growing a cull sphere is strictly conservative, oversize only
        // costs bake speed.
        float poseReach = IrliteConfig.shadowPoseReach();
        if (!(poseReach >= 0F))
        {
            poseReach = 1.0F;
        }
        float slack = Math.max(OVERLAP_MARGIN, poseReach * ehy);
        float radius = (float) Math.sqrt((double) ehx * ehx + (double) ehy * ehy + (double) ehz * ehz) + slack;

        // INVARIANT 2 (CONSERVATIVE, LOCKED): mark ALL model blocks dynamic. An
        // animated model block left isStatic=true would FREEZE its shadow at the first
        // pose; isStatic=false is guaranteed-correct. occType stays MODEL_BLOCK
        // regardless of isStatic (independent axes).
        // TODO(Ф3 perf-debt, Open Q1): mark genuinely-static model-blocks isStatic=true
        // once a safe BBS animation probe is confirmed — see shadow-phase3-port-plan.md §8.
        boolean isStatic = false;
        // INVARIANT 3: per-caster CONTENT only (the order-independent cross-caster
        // avalanche+count fold lives in the shared ShadowBaker). Non-static casters
        // supply EXACTLY 0L; dead today (isStatic hardcoded false).
        long staticHash = isStatic
            ? modelBlockHash((float) cx, (float) cy, (float) cz, t, System.identityHashCode(form))
            : 0L;

        sink.emit(mbe, CasterType.MODEL_BLOCK, isStatic, (float) cx, (float) cy, (float) cz, radius, staticHash);
    }

    Transform foldFormChain(Form form)
    {
        foldScale[0] = this.foldScale[1] = this.foldScale[2] = 1F;
        foldTranslate[0] = this.foldTranslate[1] = this.foldTranslate[2] = 0F;
        foldSignY = 1F;
        try
        {
            Transform ft = form.transform.get();
            Transform ov = form.transformOverlay.get();
            float sx = ft == null ? 1F : ft.scale.x;
            float sy = ft == null ? 1F : ft.scale.y;
            float sz = ft == null ? 1F : ft.scale.z;
            float tx = ft == null ? 0F : ft.translate.x;
            float ty = ft == null ? 0F : ft.translate.y;
            float tz = ft == null ? 0F : ft.translate.z;
            if (ov != null)
            {
                sx += ov.scale.x - 1F;
                sy += ov.scale.y - 1F;
                sz += ov.scale.z - 1F;
                tx += ov.translate.x;
                ty += ov.translate.y;
                tz += ov.translate.z;
            }
            List<ValueTransform> extra = form.additionalTransforms;
            if (extra != null)
            {
                for (int i = 0, n = extra.size(); i < n; i++)
                {
                    ValueTransform vt = extra.get(i);
                    Transform at = vt == null ? null : vt.get();
                    if (at != null)
                    {
                        sx += at.scale.x - 1F;
                        sy += at.scale.y - 1F;
                        sz += at.scale.z - 1F;
                        tx += at.translate.x;
                        ty += at.translate.y;
                        tz += at.translate.z;
                    }
                }
            }

            float cfgX = 1F, cfgY = 1F, cfgZ = 1F;
            if (form instanceof ModelForm mf)
            {
                // Own try: config-scale drift must degrade ONLY the config
                // factor, never the user transform fold already computed above.
                try
                {
                    ModelInstance model = BBSModClient.getModels().models.get(mf.model.get());
                    Vector3f s = model == null ? null : modelConfigScale(model);
                    if (s != null)
                    {
                        cfgX = s.x; cfgY = s.y; cfgZ = s.z;
                    }
                }
                catch (Throwable ignored)
                {
                }
            }

            foldSignY = sy * cfgY < 0F ? -1F : 1F;
            foldScale[0] = Math.max(1F, Math.abs(sx * cfgX));
            foldScale[1] = Math.max(1F, Math.abs(sy * cfgY));
            foldScale[2] = Math.max(1F, Math.abs(sz * cfgZ));
            foldTranslate[0] = tx;
            foldTranslate[1] = ty;
            foldTranslate[2] = tz;
            return ft;
        }
        catch (Throwable t)
        {
            foldScale[0] = this.foldScale[1] = this.foldScale[2] = 1F;
            foldTranslate[0] = this.foldTranslate[1] = this.foldTranslate[2] = 0F;
            foldSignY = 1F;
            return null;
        }
    }

    private Vector3f modelConfigScale(ModelInstance model)
    {
        try
        {
            return BbsSilhouetteBridge.scale(model);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    void expandInnerBox(Transform ft, float ihx, float ihy, float ihz)
    {
        float sy = ihy * this.foldSignY;
        if (ft == null)
        {
            innerExt[0] = ihx; this.innerExt[1] = ihy; this.innerExt[2] = ihz;
            innerCenter[0] = this.foldTranslate[0];
            innerCenter[1] = this.foldTranslate[1] + sy;
            innerCenter[2] = this.foldTranslate[2];
            return;
        }
        Matrix3f rf = ft.createRotationMatrix();
        innerExt[0] = Math.abs(rf.m00) * ihx + Math.abs(rf.m10) * ihy + Math.abs(rf.m20) * ihz;
        innerExt[1] = Math.abs(rf.m01) * ihx + Math.abs(rf.m11) * ihy + Math.abs(rf.m21) * ihz;
        innerExt[2] = Math.abs(rf.m02) * ihx + Math.abs(rf.m12) * ihy + Math.abs(rf.m22) * ihz;
        innerCenter[0] = this.foldTranslate[0] + rf.m10 * sy;
        innerCenter[1] = this.foldTranslate[1] + rf.m11 * sy;
        innerCenter[2] = this.foldTranslate[2] + rf.m12 * sy;
    }

    boolean foldIsIdentity(Transform ft)
    {
        if (this.foldScale[0] != 1F || this.foldScale[1] != 1F || this.foldScale[2] != 1F
            || this.foldSignY != 1F
            || this.foldTranslate[0] != 0F || this.foldTranslate[1] != 0F || this.foldTranslate[2] != 0F)
        {
            return false;
        }
        return ft == null || (ft.rotate.x == 0F && ft.rotate.y == 0F && ft.rotate.z == 0F);
    }

    private long modelBlockHash(float wx, float wy, float wz, Transform t, int formIdentity)
    {
        long h = FNV_OFFSET;
        h = (h ^ (formIdentity & 0xffffffffL)) * FNV_PRIME;
        h = mix(h, wx); h = mix(h, wy); h = mix(h, wz);
        if (t != null)
        {
            h = mix(h, t.scale.x); h = mix(h, t.scale.y); h = mix(h, t.scale.z);
            h = mix(h, t.rotate.x); h = mix(h, t.rotate.y); h = mix(h, t.rotate.z);
            // BBS 2.2.1 removed Transform.rotate2 — the legacy second rotation now
            // folds into the single `rotate`, so hashing `rotate` alone is sufficient.
        }
        // WARNING (Ф3 audit): this deliberately covers only form-IDENTITY + Transform,
        // NOT the form's internal morph/animation pose. It is dead today (isStatic is
        // hardcoded false). If the Open-Q1 animation probe is ever enabled, isStatic=true
        // MUST be gated on a confirmed NON-animating morph — a stable Transform alone is
        // NOT sufficient, or the old animated-block freeze (INVARIANT 2) returns.
        return h;
    }

    private long mix(long h, float v)
    {
        return (h ^ (Float.floatToRawIntBits(v) & 0xffffffffL)) * FNV_PRIME;
    }
}
