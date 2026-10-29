package org.qualet.irl.light.shadow;

import qualet.irlite.platform.RenderApi;

import mchorse.bbs_mod.client.renderer.MorphRenderer;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Native immediate backend; Core owns its buffers and pass matrices. */
public final class CasterPass implements AutoCloseable
{
    private final ImmediateOccluderBatch batch;

    public CasterPass(OccluderBatch batch)
    {
        this.batch = (ImmediateOccluderBatch) batch;
    }

    public MatrixStack matrices()
    {
        return this.batch.matrices();
    }

    public VertexConsumerProvider.Immediate buffers()
    {
        return this.batch.immediate();
    }

    public void draw(Runnable render)
    {
        render.run();
    }

    public void renderEntity(Entity entity, double x, double y, double z, float yaw, float delta, int light)
    {
        MatrixStack stack = this.matrices();
        boolean rendered = false;
        stack.push();
        try
        {
            stack.translate(x, y, z);
            if (entity instanceof AbstractClientPlayerEntity player)
            {
                rendered = MorphRenderer.renderPlayer(player, yaw, delta, stack, this.buffers(), light);
            }
            if (!rendered && entity instanceof LivingEntity living)
            {
                rendered = MorphRenderer.renderLivingEntity(living, yaw, delta, stack, this.buffers(), light, RenderApi.overlay(living, delta));
            }
        }
        finally
        {
            stack.pop();
        }
        if (!rendered)
        {
            RenderApi.renderEntity(entity, x, y, z, yaw, delta, stack, this.buffers(), light);
        }
    }

    @Override
    public void close()
    {}
}
