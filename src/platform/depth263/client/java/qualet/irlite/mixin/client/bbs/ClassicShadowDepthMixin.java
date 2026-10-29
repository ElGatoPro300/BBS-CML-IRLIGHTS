package qualet.irlite.mixin.client.bbs;

import org.qualet.irl.light.shadow.ShadowBakeState;

import mchorse.bbs_mod.graphics.ImmediateDraw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.backend.opengl.GlStateManager;

import org.lwjgl.opengl.GL11;

/** Core atlases retain classic depth while CML's world pipelines use reverse-Z. */
@Mixin(value = ImmediateDraw.class, remap = false)
public class ClassicShadowDepthMixin
{
    @Inject(method = "applyDepthStencil", at = @At("TAIL"))
    private static void irlite$classicCasterDepth(CallbackInfo callback)
    {
        if (ShadowBakeState.isBaking())
        {
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._depthFunc(GL11.GL_LEQUAL);
        }
    }
}
