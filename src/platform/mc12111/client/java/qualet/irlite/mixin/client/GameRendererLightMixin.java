package qualet.irlite.mixin.client;

import qualet.irlite.client.render.LightFramePipeline;
import qualet.irlite.client.runtime.ClientRuntime;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererLightMixin
{
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void irlite$collectLights(RenderTickCounter tickCounter, CallbackInfo ci)
    {
        /* This engine updates Camera in render(), before renderWorld begins. */
        LightFramePipeline.begin(tickCounter.getTickProgress(true));
        LightFramePipeline.upload();
    }

    /** Iris has finished world compositing; BBS has not captured the film preview yet. */
    @Inject(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        shift = At.Shift.AFTER), require = 1)
    private void irlite$drawLightGuides(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci)
    {
        ClientRuntime.get().guides().flush();
    }
}
