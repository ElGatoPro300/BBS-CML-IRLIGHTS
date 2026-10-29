package qualet.irlite.mixin.client;

import qualet.irlite.client.render.LightFramePipeline;
import qualet.irlite.client.runtime.ClientRuntime;

import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void irlite$collectLights(CallbackInfo ci)
    {
        /* Extraction captured the camera and interpolation for this rendered frame. */
        LightFramePipeline.begin(((GameRenderer) (Object) this).gameRenderState().levelRenderState.worldPartialTicks);
        LightFramePipeline.upload();
    }

    /** Iris has finished world compositing; BBS has not captured the film preview yet. */
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V",
        shift = At.Shift.AFTER), require = 1)
    private void irlite$drawLightGuides(CallbackInfo ci)
    {
        ClientRuntime.get().guides().flush();
    }
}
