package qualet.irlite.client.forms;

import qualet.irlite.platform.RenderApi;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.RenderPassState;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Snapshot world guides before Iris can light, overwrite or discard them. Draw them
 * after renderWorld, while BBS still owns its film/export framebuffer. Preview and
 * picking draws stay immediate, as do guides inside framebuffer forms. */
public final class WorldLightGuideOverlay
{
    private record Guide(Matrix4f pose, Matrix4f modelView, Matrix4f projection,
                         Color color, float range, float outer, float inner, boolean spot)
    {}

    private final List<Guide> pending = new ArrayList<>();

    public WorldLightGuideOverlay()
    {}

    public void beginFrame()
    {
        pending.clear();
    }

    boolean defer(MatrixStack stack, Color color, float range, float outer, float inner, boolean spot)
    {
        // A loaded pack alone is insufficient: nested framebuffer forms keep their
        // world context type but suspend the Iris override while drawing offscreen.
        if (!BBSRendering.isIrisWorldShadersEnabled())
        {
            return false;
        }

        pending.add(new Guide(new Matrix4f(stack.peek().getPositionMatrix()),
            new Matrix4f(RenderApi.modelViewMatrix()), new Matrix4f(RenderApi.projectionMatrix()),
            color.copy(), range, outer, inner, spot));

        return true;
    }

    public void flush()
    {
        if (this.pending.isEmpty())
        {
            return;
        }

        RenderApi.ProjectionState projection = new RenderApi.ProjectionState();
        Matrix4f oldModelView = new Matrix4f(RenderApi.modelViewMatrix());
        RenderApi.pushModelView();
        try (RenderPassState state = new RenderPassState(); RenderApi.ColorState color = new RenderApi.ColorState())
        {
            RenderApi.bindMain();
            RenderApi.setShaderColor(1F, 1F, 1F, 1F);

            for (Guide guide : this.pending)
            {
                RenderApi.modelView(guide.modelView());
                RenderApi.projection(guide.projection());
                MatrixStack stack = new MatrixStack();
                stack.peek().getPositionMatrix().set(guide.pose());

                if (guide.spot())
                {
                    LightGuideRenderer.renderSpotlight(stack, guide.color(), guide.range(), guide.outer(), guide.inner());
                }
                else
                {
                    LightGuideRenderer.renderPointLight(stack, guide.color(), guide.range());
                }
            }
        }
        finally
        {
            pending.clear();
            RenderApi.popModelView();
            // The applied matrix can differ from the stack top (BBS's UI does this).
            RenderApi.pushModelView();
            RenderApi.modelView(oldModelView);
            RenderApi.popModelView();
            projection.close();

        }
    }
}
