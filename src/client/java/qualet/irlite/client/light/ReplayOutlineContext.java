package qualet.irlite.client.light;

import qualet.irlite.client.runtime.ClientRuntime;
import qualet.irlite.mixin.client.bbs.FilmsAccessor;

import org.qualet.irl.light.shadow.ShadowBakeState;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;

import net.irisshaders.iris.gl.state.ValueUpdateNotifier;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import io.netty.util.collection.IntObjectMap;

/**
 * Which replay is being drawn right now, as a small integer the shader can read.
 *
 * <p>Both replay filters (who a light lights, who it outlines) need the shader to know,
 * per pixel, which replay put that pixel there. This class hands out one token per
 * (film, replay) — stable for as long as the world lives, so a replay keeps its number
 * from frame to frame — tags every film entity with its token at the start of the frame,
 * and while a form renders exposes the token as the Iris uniform {@code irlite_replayId}.
 * The patched gbuffers write it (with depth) into an extra colour attachment for the
 * deferred outline; the forward surface pass reads the uniform directly.</p>
 *
 * <p>Body parts may use their own entity. Their form ancestry identifies the owning
 * replay even when BBS postpones a part until the end of the render pass. Vanilla
 * batches entity geometry, so the batch is flushed at every tag change — otherwise a
 * form's triangles would be drawn later, under whatever tag was current then.</p>
 *
 * <p>Zero means "no replay": UI previews, picking, the Iris shadow pass and the mod's
 * own shadow bake all render untagged, and a bare light form is never tagged (it cannot
 * be a target — see {@link ReplaySelection#isSelectable}).</p>
 */
public final class ReplayOutlineContext
{
    /** Iris uniform updates use the session-owned draw scope. */
    public static final ValueUpdateNotifier NOTIFIER = value -> ClientRuntime.get().replays().listener(value);

    private ReplayOutlineContext()
    {}

    public static int currentId()
    {
        return ClientRuntime.get().replays().currentId();
    }

    /** The token of {@code replay} in {@code film} this frame, or 0 when it has no entity. */
    public static int resolve(String film, String replay)
    {
        return ClientRuntime.get().replays().resolve(film, replay);
    }

    /** Once per frame, before any form renders: re-tag the film entities. */
    public static void beginFrame()
    {
        Object now = MinecraftClient.getInstance().world;

        ClientRuntime.get().replays().beginFrame(now);

        if (now == null)
        {
            return;
        }

        Films films = BBSModClient.getFilms();

        if (films != null)
        {
            for (BaseFilmController controller : ((FilmsAccessor) (Object) films).irlite$getControllers())
            {
                collect(controller);
            }
        }

        collect(LightCollector.getActiveEditorController());
    }

    private static void collect(BaseFilmController controller)
    {
        if (controller == null || controller.film == null)
        {
            return;
        }

        String film = controller.film.getId();
        IntObjectMap<IEntity> entities = controller.getEntities();

        for (Replay replay : controller.film.replays.getList())
        {
            IEntity entity = entities.get(controller.film.replays.getList().indexOf(replay));

            if (entity == null || !replay.enabled.get())
            {
                continue;
            }

            if (!ReplaySelection.isSelectable(replay.form.get()) || !ReplaySelection.isSelectable(entity.getForm()))
            {
                continue;
            }

            ClientRuntime.get().replays().register(film, replay.getId(), entity, entity.getForm());
        }
    }

    /** Resolve ownership from the actual form tree, never from the preceding draw. */
    static int renderId(Form form, IEntity entity)
    {
        Integer token = ClientRuntime.get().replays().entity(entity);

        if (token != null)
        {
            return token;
        }

        for (Form owner = form; owner != null; owner = owner.getParentForm())
        {
            token = ClientRuntime.get().replays().form(owner);

            if (token != null)
            {
                return token;
            }
        }

        return 0;
    }

    private static void set(int id)
    {
        ClientRuntime.get().replays().set(id);
    }

    /** Retain replay ownership when CML postpones geometry until a later render pass. */
    public static Runnable captureDeferred(Runnable draw)
    {
        return ClientRuntime.get().replays().capture(draw);
    }

    /**
     * Render {@code renderer} under its entity's or owning root form's replay tag. Called in place of
     * {@code FormRenderer.render} by {@code FormUtilsClientMixin}.
     */
    public static void render(FormRenderer<?> renderer, FormRenderingContext context)
    {
        int previous = currentId();
        boolean untagged = context.ui || context.isPicking() || BBSRendering.isIrisShadowPass() || ShadowBakeState.isBaking();
        int id = untagged ? 0 : renderId(renderer.getForm(), context.entity);

        if (id == previous)
        {
            renderer.render(context);

            return;
        }

        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        /* ModelFormRenderer draws cubic BBS models and BOBJ/OBJ through BBS's own
         * immediate/VAO pipeline. It never queues their faces in Minecraft's shared
         * entity Immediate, so flushing that unrelated buffer here only perturbs the
         * GL state ModelFormRenderer expects on entry. On BBS 2.6 that manifested as
         * front-face culling: only the models' inside faces remained visible, even
         * with Iris shaders disabled. Keep the replay uniform scope, but leave the
         * shared batch untouched for this synchronous renderer. */
        if (renderer instanceof ModelFormRenderer)
        {
            set(id);

            try
            {
                renderer.render(context);
            }
            finally
            {
                set(previous);
            }

            return;
        }

        drawPreservingCull(consumers);
        set(id);

        try
        {
            renderer.render(context);
        }
        finally
        {
            try
            {
                drawPreservingCull(consumers);
            }
            finally
            {
                set(previous);
            }
        }
    }

    /** A tag boundary may flush arbitrary vanilla layers. Do not let their
     * culling phase become the input state of the BBS renderer that follows. */
    private static void drawPreservingCull(VertexConsumerProvider.Immediate consumers)
    {
        boolean enabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int face = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int winding = GL11.glGetInteger(GL11.GL_FRONT_FACE);

        try
        {
            consumers.draw();
        }
        finally
        {
            GL11.glCullFace(face);
            GL11.glFrontFace(winding);

            if (enabled)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }
        }
    }
}
