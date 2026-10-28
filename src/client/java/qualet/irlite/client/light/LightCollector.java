package qualet.irlite.client.light;

import qualet.irlite.client.adapter.cml.CmlFilmAccess;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/** Shared collection coordinator; scene sources do not own traversal or GPU policy. */
public final class LightCollector
{
    public static final double MAX_DIST = 256D;
    public static final boolean VL_BILATERAL = !Boolean.getBoolean("irlite.vlNoBilateral");
    private static final ThreadLocal<TraversalScratch> SCRATCH = ThreadLocal.withInitial(TraversalScratch::new);

    static final class TraversalScratch
    {
        private final List<ScratchFrame> frames = new ArrayList<>();
        private int depth;

        int mark() { return depth; }
        ScratchFrame push()
        {
            if (depth == frames.size()) frames.add(new ScratchFrame());
            return frames.get(depth++);
        }
        void rewind(int mark) { depth = mark; }
    }

    static final class ScratchFrame
    {
        final Matrix4f local = new Matrix4f();
        final Matrix4f transform = new Matrix4f();
        final Matrix4f child = new Matrix4f();
        final Vector4f origin = new Vector4f();
        final Vector4f forward = new Vector4f();
    }

    private LightCollector()
    {}

    public static boolean isHandledByScanner(FormRenderingContext context)
    {
        if (context == null)
        {
            return false;
        }
        if (context.type == FormRenderType.MODEL_BLOCK)
        {
            return true;
        }
        if (context.type == FormRenderType.ENTITY)
        {
            IEntity entity = context.entity;
            if (entity == null)
            {
                return false;
            }
            FilmEditorController editor = CmlFilmAccess.activeEditor(true);
            if (editor == null)
            {
                return false;
            }
            try
            {
                for (IEntity rosterEntity : editor.getEntities().values())
                {
                    if (rosterEntity == entity)
                    {
                        return true;
                    }
                }
            }
            catch (Throwable t)
            {
                return false;
            }
        }
        return false;
    }

    public static FilmEditorController getActiveEditorController()
    {
        return CmlFilmAccess.activeEditor(true);
    }

    public static void collect(ClientWorld world, Vec3d camera, float tickDelta)
    {
        LightGlobals.apply();

        if (world == null || camera == null)
        {
            return;
        }

        TraversalScratch scratch = SCRATCH.get();
        ModelBlockLightSource.collect(world, camera, tickDelta, scratch);
        FilmReplayLightSource.collect(camera, tickDelta, scratch);
    }
}
