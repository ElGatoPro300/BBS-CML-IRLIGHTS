package qualet.irlite.mixin.client;

import qualet.irlite.client.render.LightFramePipeline;
import qualet.irlite.client.runtime.ClientRuntime;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        LightFramePipeline.begin(tickDelta);
    }

    /**
     * Deferred SSBO upload, injected just AFTER this frame's Camera.update (offset ~180
     * in renderWorld, still well before WorldRenderer.render / Iris activation at ~562):
     * the origin the light SSBO is made relative to must be the post-update, current-frame
     * eye that the shaderpack reconstructs fragments against, not the stale HEAD camera.
     */
    @Inject(method = "renderWorld",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
                     shift = At.Shift.AFTER,
                     ordinal = 0),
            require = 1)
    private void irlite$uploadLights(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci)
    {
        LightFramePipeline.upload();
    }

    /** Iris has finished world compositing; BBS has not captured the film preview yet. */
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V",
        shift = At.Shift.AFTER), require = 1)
    private void irlite$drawLightGuides(float tickDelta, long startTime, boolean tick, CallbackInfo ci)
    {
        ClientRuntime.get().guides().flush();
    }
}
