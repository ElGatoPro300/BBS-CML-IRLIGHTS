package qualet.irlite.mixin.client.bbs;

import qualet.irlite.client.light.BbsSoftTransparency;

import mchorse.bbs_mod.client.rendering.soft.SoftDrawEntry;
import mchorse.bbs_mod.client.rendering.soft.SoftEntryRunner;
import mchorse.bbs_mod.rendering.soft.SoftDrawSpec;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also covers BBS's plain shader callbacks in the no-shading queue. */
@Mixin(value = SoftEntryRunner.class, remap = false)
public class SoftEntryRunnerMixin
{
    @Inject(method = "run(Lmchorse/bbs_mod/rendering/soft/SoftDrawSpec;Lmchorse/bbs_mod/client/rendering/soft/SoftDrawEntry;Z)V",
        at = @At(value = "INVOKE", target = "Lmchorse/bbs_mod/client/rendering/soft/SoftDrawEntry;draw()Ljava/lang/Runnable;"))
    private void irlite$blendSurface(SoftDrawSpec spec, SoftDrawEntry entry, boolean depthTest, CallbackInfo ci)
    {
        BbsSoftTransparency.applySurfaceBlend();
    }
}
