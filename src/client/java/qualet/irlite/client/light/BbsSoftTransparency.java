package qualet.irlite.client.light;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.rendering.soft.SoftTransparency;

import net.irisshaders.iris.gl.IrisRenderSystem;

import org.lwjgl.opengl.GL11;

/** CML soft draws need surface blending even while Iris locks its replay attachment's state. */
public final class BbsSoftTransparency
{
    private BbsSoftTransparency()
    {}

    public static void applySurfaceBlend()
    {
        SoftTransparency soft = BBSModClient.getSoftTransparency();

        if (soft == null || (!soft.getQueue().isFlushing() && !soft.getPostComposite().isFlushing()))
        {
            return;
        }

        /* Indexed calls bypass Iris's global blend lock without changing replay-ID attachments.
         * Match RenderSystem.defaultBlendFunc for the surface output only. Iris restores its state
         * when the shader is unbound; ordinary world draws never enter this branch. */
        IrisRenderSystem.enableBufferBlend(0);
        IrisRenderSystem.blendFuncSeparatei(0, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }
}
