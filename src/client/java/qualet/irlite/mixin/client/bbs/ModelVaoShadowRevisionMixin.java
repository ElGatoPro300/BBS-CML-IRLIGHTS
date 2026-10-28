package qualet.irlite.mixin.client.bbs;

import qualet.irlite.client.light.ShadowResourceRevision;
import qualet.irlite.client.light.ShadowResourceVersions;

import mchorse.bbs_mod.cubic.render.vao.ModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelVAO.class, remap = false)
public class ModelVaoShadowRevisionMixin implements ShadowResourceRevision
{
    @Unique private long irlite$revision;

    @Inject(method = {"upload", "delete"}, at = @At("HEAD"), require = 0)
    private void irlite$changed(CallbackInfo ci) { irlite$revision = ShadowResourceVersions.next(); }

    @Override public long irlite$shadowRevision() { return irlite$revision; }
}
