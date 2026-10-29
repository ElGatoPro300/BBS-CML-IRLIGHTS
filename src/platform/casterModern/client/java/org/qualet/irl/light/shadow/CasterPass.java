package org.qualet.irl.light.shadow;

import qualet.irlite.platform.RenderApi;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.EntityPreviewRenderHelper;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.graphics.RenderPassState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Submits real CML geometry into Core's currently bound depth-only pass. */
public final class CasterPass implements AutoCloseable
{
    private final MatrixStack stack = new MatrixStack();
    private final RenderPassState state;
    private final RenderApi.ProjectionState projection;
    private final RenderApi.ColorState color;

    public CasterPass(OccluderBatch batch)
    {
        if (!(batch instanceof RawOccluderBatch))
        {
            throw new IllegalArgumentException("Native caster adapter requires a raw Core pass");
        }
        this.state = new RenderPassState();
        this.projection = new RenderApi.ProjectionState();
        this.color = new RenderApi.ColorState();
        RenderApi.pushModelView();
        try
        {
            RenderApi.modelView(ShadowRenderer.currentViewMatrix());
            RenderApi.projection(ShadowRenderer.currentProjectionMatrix());
            RenderApi.setShaderColor(1F, 1F, 1F, 1F);
            RenderApi.classicDepth();
        }
        catch (RuntimeException | Error failure)
        {
            this.close();
            throw failure;
        }
    }

    public MatrixStack matrices()
    {
        return this.stack;
    }

    public CustomVertexConsumerProvider buffers()
    {
        return FormUtilsClient.getProvider();
    }

    public void draw(Runnable render)
    {
        BBSRendering.renderOffscreen(() ->
        {
            try
            {
                render.run();
            }
            finally
            {
                this.buffers().draw();
            }
        });
    }

    public void renderEntity(Entity entity, double x, double y, double z, float yaw, float delta, int light)
    {
        this.stack.push();
        try
        {
            this.stack.translate(x, y, z);
            boolean rendered = false;
            if (entity instanceof LivingEntity living)
            {
                LivingEntityRenderState state = (LivingEntityRenderState) MinecraftClient.getInstance()
                    .getEntityRenderDispatcher().getRenderer(living).getAndUpdateRenderState(living, delta);
                if (entity instanceof AbstractClientPlayerEntity player)
                {
                    rendered = MorphRenderer.renderPlayer(player, state, delta, this.stack, this.buffers(), light);
                }
                if (!rendered)
                {
                    rendered = MorphRenderer.renderLivingEntity(living, yaw, delta, this.stack, this.buffers(), light,
                        LivingEntityRenderer.getOverlay(state, 0F));
                }
            }
            if (!rendered)
            {
                EntityPreviewRenderHelper.renderEntity(entity, this.stack, delta, light);
            }
        }
        finally
        {
            this.stack.pop();
        }
    }

    @Override
    public void close()
    {
        RenderApi.popModelView();
        this.color.close();
        this.state.close();
        this.projection.close();
    }
}
