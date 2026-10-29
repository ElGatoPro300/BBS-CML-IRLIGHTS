package org.qualet.irl.light.shadow;

import qualet.irlite.platform.RenderApi;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

/** Draws shortlisted casters; Core owns pass setup, flush and failure isolation. */
final class CasterGeometry
{
    private static final int FULL_LIGHT = RenderApi.fullLight();

    public void emit(Object caster, int type, float tickDelta, OccluderBatch batch)
    {
        try (CasterPass pass = new CasterPass(batch))
        {
            Camera camera = RenderApi.camera();
            pass.draw(() ->
            {
                switch (type)
                {
                    case CasterType.MODEL_BLOCK -> drawModelBlock((ModelBlockEntity) caster, pass.matrices(), camera, tickDelta);
                    case CasterType.REPLAY -> drawReplay((IEntity) caster, pass.matrices(), camera, tickDelta);
                    default -> drawEntity((Entity) caster, pass, tickDelta);
                }
            });
        }
    }

    private static void drawEntity(Entity entity, CasterPass pass, float tickDelta)
    {
        // Light-relative bake: emit geometry relative to the pass anchor (= the
        // light view's origin, ShadowRenderer.currentOrigin*). Subtract in double
        // BEFORE the float cast inside matrices.translate / dispatcher.render so
        // the offsets stay sub-block-magnitude far from the world origin.
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double cx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - ox;
        double cy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - oy;
        double cz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - oz;
        float yaw = entity.getYaw(tickDelta);

        pass.renderEntity(entity, cx, cy, cz, yaw, tickDelta, FULL_LIGHT);
    }

    private static void drawModelBlock(ModelBlockEntity mbe, MatrixStack matrices, Camera camera, float tickDelta)
    {
        if (mbe.getProperties() == null)
        {
            return;
        }
        Form form = mbe.getProperties().getForm();
        if (form == null)
        {
            return;
        }
        Transform t = mbe.getProperties().getTransform();

        // Feet = block center at pos.getY() (no +0.5 in Y); t.translate is NOT
        // folded in — applyTransform below applies it ONCE, exactly like the
        // BBS render (ModelBlockEntityRenderer: translate(0.5,0,0.5) then
        // applyTransform). The old code folded translate into the feet AND let
        // applyTransform add it again, displacing every baked silhouette by one
        // translate vector from the visible model (review 2026-08-10; the
        // sphere pivot in emitModelBlock moved to 1× in the same commit — the
        // two halves must always agree or the cull starts blinking). Light-
        // relative bake: subtract the pass anchor (= the light view origin) in
        // double before matrices.translate's float cast.
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double feetX = mbe.getPos().getX() + 0.5 - ox;
        double feetY = mbe.getPos().getY() - oy;
        double feetZ = mbe.getPos().getZ() + 0.5 - oz;

        matrices.push();
        matrices.translate(feetX, feetY, feetZ);
        if (t != null)
        {
            MatrixStackUtils.applyTransform(matrices, t);
        }
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
        if (renderer != null)
        {
            renderer.render(new FormRenderingContext()
                .set(FormRenderType.MODEL_BLOCK, mbe.getEntity(), matrices, FULL_LIGHT, OverlayTexture.DEFAULT_UV, tickDelta)
                .camera(camera));
        }
        matrices.pop();
    }

    private static void drawReplay(IEntity stub, MatrixStack matrices, Camera camera, float tickDelta)
    {
        Form form = stub.getForm();
        if (form == null)
        {
            return;
        }

        // Light-relative bake: subtract the pass anchor (= the light view origin)
        // in double before matrices.translate's float cast. The Y-rotation below
        // is unaffected (it is about the anchor-relative feet, orientation-only).
        double ox = ShadowRenderer.currentOriginX();
        double oy = ShadowRenderer.currentOriginY();
        double oz = ShadowRenderer.currentOriginZ();
        double fx = MathHelper.lerp(tickDelta, stub.getPrevX(), stub.getX()) - ox;
        double fy = MathHelper.lerp(tickDelta, stub.getPrevY(), stub.getY()) - oy;
        double fz = MathHelper.lerp(tickDelta, stub.getPrevZ(), stub.getZ()) - oz;
        float bodyYaw = MathHelper.lerp(tickDelta, stub.getPrevBodyYaw(), stub.getBodyYaw());

        matrices.push();
        matrices.translate(fx, fy, fz);
        RenderApi.rotateYaw(matrices, -bodyYaw);
        FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
        if (renderer != null)
        {
            renderer.render(new FormRenderingContext()
                .set(FormRenderType.ENTITY, stub, matrices, FULL_LIGHT, OverlayTexture.DEFAULT_UV, tickDelta)
                .camera(camera));
        }
        matrices.pop();
    }
}
