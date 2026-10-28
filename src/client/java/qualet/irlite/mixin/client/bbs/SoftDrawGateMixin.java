package qualet.irlite.mixin.client.bbs;

import org.qualet.irl.light.shadow.ShadowBakeState;

import mchorse.bbs_mod.client.rendering.soft.SoftDrawGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** IRLights's own shadow maps are synchronous, just like the Iris shadow pass. */
@Mixin(value = SoftDrawGate.class, remap = false)
public class SoftDrawGateMixin
{
    @Inject(method = {"canDefer", "canDeferPostComposite"}, at = @At("HEAD"), cancellable = true)
    private void irlite$keepShadowCastersLive(boolean preview, boolean picking, CallbackInfoReturnable<Boolean> cir)
    {
        if (ShadowBakeState.isBaking())
        {
            cir.setReturnValue(false);
        }
    }
}
