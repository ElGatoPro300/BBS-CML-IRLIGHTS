package qualet.irlite.mixin.client.bbs;

import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import org.qualet.irl.light.shadow.OccluderCaptureSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Diverts draws into the open occluder capture session instead of the framebuffer. */
@Mixin(RenderLayer.class)
public class RenderLayerCaptureMixin
{
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, require = 1)
    private void irlite$capture(BuiltBuffer buffer, CallbackInfo ci)
    {
        if (OccluderCaptureSession.isActive())
        {
            OccluderCaptureSession.divert((RenderLayer) (Object) this, buffer);
            ci.cancel();
        }
    }
}
