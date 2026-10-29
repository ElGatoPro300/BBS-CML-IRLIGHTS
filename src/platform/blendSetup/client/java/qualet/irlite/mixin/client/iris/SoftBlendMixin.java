package qualet.irlite.mixin.client.iris;

import qualet.irlite.client.light.BbsSoftTransparency;

import net.irisshaders.iris.pipeline.programs.ExtendedShader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Iris applies attachment overrides again when the queued mesh binds its entity shader. */
@Mixin(value = ExtendedShader.class, remap = false)
public class SoftBlendMixin
{
    @Inject(method = "iris$setupState", at = @At("TAIL"))
    private void irlite$blendSoftSurface(CallbackInfo ci)
    {
        BbsSoftTransparency.applySurfaceBlend();
    }
}
