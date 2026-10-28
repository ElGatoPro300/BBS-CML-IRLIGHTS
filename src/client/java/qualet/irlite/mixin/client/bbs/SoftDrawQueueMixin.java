package qualet.irlite.mixin.client.bbs;

import qualet.irlite.client.light.ReplayOutlineContext;

import mchorse.bbs_mod.client.rendering.soft.SoftDrawQueue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Both soft and no-shading queues retain the submitting replay's uniform scope. */
@Mixin(value = SoftDrawQueue.class, remap = false)
public class SoftDrawQueueMixin
{
    @ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Runnable irlite$captureReplay(Runnable draw)
    {
        return ReplayOutlineContext.captureDeferred(draw);
    }
}
