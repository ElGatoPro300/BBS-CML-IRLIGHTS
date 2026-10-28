package qualet.irlite.client.forms;

import qualet.irlite.client.ui.forms.editors.panels.UISpotlightFormPanel;
import qualet.irlite.domain.guide.SpotGuideMath;
import qualet.irlite.forms.SpotlightForm;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Drag state + math for the interactive spotlight guide handles in the form
 * editor preview ({@link UIPickableFormRenderer}).
 *
 * <p>The handles are extra entries in BBS's stencil picking pass (registered
 * by {@link SpotlightFormRenderer#renderStencilHandles}), named with the
 * {@link #HANDLE_RADIUS}/{@link #HANDLE_INNER}/{@link #HANDLE_RANGE} pseudo-bone
 * strings. A mixin on the renderer routes clicks on those entries here instead
 * of BBS's form/bone picking, then calls {@link #update} once per frame.</p>
 *
 * <p>All math runs in guide-local space. The local&rarr;view matrix is captured
 * each frame at visible-guide render time ({@link #captureGuideMatrix}); the
 * mouse ray is built in the preview camera's frame (BBS convention: the stack
 * is {@code view * translate(-cam.pos) * chain}, see {@code Gizmo#computeWorldOrigin})
 * and transformed into that local space, where the cap plane and the axis are
 * trivial. Values are written straight into the form's {@code ValueFloat}s —
 * the form editor's {@code preCallback} undo hook picks them up like any
 * trackpad edit.</p>
 */
public final class SpotGuideDrag
{
    public static final String HANDLE_RADIUS = "radius";
    public static final String HANDLE_INNER = "inner_radius";
    public static final String HANDLE_RANGE = "range";

    private static final float EPSILON = 1e-5F;

    /** Guide local->view matrices captured at visible render, per live form. */
    private final Map<SpotlightForm, Matrix4f> guideMatrices = new WeakHashMap<>();

    private WeakReference<UISpotlightFormPanel> panel = new WeakReference<>(null);
    private WeakReference<UIFilmPanel> filmPanelCache = new WeakReference<>(null);

    /* Active drag session. Host identifies which viewport owns the drag
     * (UIPickableFormRenderer in the form editor, UIFilmController in the
     * film editor); camera/viewport are live references from that host. */
    private SpotlightForm dragForm;
    private String dragHandle;
    private Object dragHost;
    private Camera dragCamera;
    private Area dragViewport;

    /** Null in the form preview; film gestures follow BBS's auto-keyframe setting. */
    private SpotGuideKeyframes filmKeyframes;
    private int dragMouseX;
    private int dragMouseY;
    private boolean dragMoved;

    public SpotGuideDrag()
    {}

    public void reset()
    {
        this.stop();
        this.guideMatrices.clear();
        this.filmSelected.clear();
        this.panel = new WeakReference<>(null);
        this.filmPanelCache = new WeakReference<>(null);
    }

    /** The active spotlight panel registers itself so drags can live-sync its trackpads. */
    public void bindPanel(UISpotlightFormPanel activePanel)
    {
        panel = new WeakReference<>(activePanel);
    }

    /** Captured from the editor-preview visible pass so the handles track the rendered guide. */
    public void captureGuideMatrix(SpotlightForm form, MatrixStack stack)
    {
        guideMatrices.put(form, new Matrix4f(stack.peek().getPositionMatrix()));
    }

    public boolean isDragging()
    {
        return this.dragForm != null;
    }

    /**
     * Form-editor preview: start a drag if the stencil pixel under the cursor is
     * one of our handles. Called from the renderer mixin at the head of
     * {@code subMouseClicked}; returning true consumes the click before BBS's
     * gizmo/bone picking.
     */
    public boolean tryStart(UIPickableFormRenderer renderer, UIContext context)
    {
        return tryStartWith(renderer, renderer.getStencil(), renderer.camera, renderer.area, context);
    }

    /**
     * Film editor: same, but against the film controller's stencil and the film
     * viewport camera (the exact pair BBS's own film gizmo drags with, see
     * {@code UIReplaysEditorUtils.startFilmGizmo}).
     */
    public boolean tryStartFilm(UIFilmController controller, UIContext context)
    {
        /* No drags while flying or while the cursor is grabbed (actor control
         * mode) — the stencil pick under a locked cursor is meaningless. */
        if (controller.panel.isFlying() || MinecraftClient.getInstance().mouse.isCursorLocked())
        {
            return false;
        }

        if (!tryStartWith(
            controller,
            controller.picking.getGizmoStencil(),
            controller.panel.getCamera(),
            controller.panel.preview.getViewport(),
            context
        ))
        {
            return false;
        }

        /* Keyframe gate: dragging in the film edits keyframes only. */
        filmKeyframes = resolveFilmKeyframes(controller);

        if (this.filmKeyframes == null)
        {
            stop();

            return false;
        }

        return true;
    }

    /**
     * Resolve the replay track without creating a key on a click alone.
     * With auto-keyframing off, retain the existing governing-keyframe behavior.
     */
    private SpotGuideKeyframes resolveFilmKeyframes(UIFilmController controller)
    {
        Replay replay = selectedReplay();

        if (replay == null || this.dragForm == null || this.dragHandle == null)
        {
            return null;
        }

        ValueFloat property = switch (this.dragHandle)
        {
            case HANDLE_RANGE -> this.dragForm.range;
            case HANDLE_INNER -> this.dragForm.innerRadius;
            default -> this.dragForm.radius;
        };

        return SpotGuideKeyframes.begin(replay.properties, property,
            controller.panel.getCursor(), controller.isInstantKeyframes());
    }

    private boolean tryStartWith(Object host, StencilFormFramebuffer stencil, Camera camera, Area viewport, UIContext context)
    {
        if (context.mouseButton != 0 || !stencil.hasPicked())
        {
            return false;
        }

        Pair<Form, String> pair = stencil.getPicked();

        if (pair == null || !(pair.a instanceof SpotlightForm spot) || pair.b == null)
        {
            return false;
        }

        if (!isHandle(pair.b) || !this.guideMatrices.containsKey(spot))
        {
            return false;
        }

        dragForm = spot;
        dragHandle = pair.b;
        dragHost = host;
        dragCamera = camera;
        dragViewport = viewport;
        dragMouseX = context.mouseX;
        dragMouseY = context.mouseY;
        dragMoved = false;

        return true;
    }

    /** Ends the drag; true when a drag was active (the release is then consumed). */
    public boolean mouseReleased()
    {
        if (this.dragForm == null)
        {
            return false;
        }

        stop();

        return true;
    }

    public void stop()
    {
        if (this.filmKeyframes != null)
        {
            filmKeyframes.finish();
        }

        dragForm = null;
        dragHandle = null;
        dragHost = null;
        dragCamera = null;
        dragViewport = null;
        filmKeyframes = null;
    }

    /**
     * Per-frame drag update, driven from the host mixins (form editor:
     * {@code renderUserModel} tail; film editor: {@code renderPickingPreview} head).
     */
    public void update(Object host, UIContext context)
    {
        if (this.dragForm == null || this.dragHost != host)
        {
            return;
        }

        if (host instanceof UIFilmController controller && controller.panel.isFlying())
        {
            return;
        }

        dragMoved |= context.mouseX != this.dragMouseX || context.mouseY != this.dragMouseY;

        if (this.filmKeyframes != null && !this.dragMoved)
        {
            return;
        }

        Matrix4f guide = this.guideMatrices.get(this.dragForm);

        if (guide == null)
        {
            stop();

            return;
        }

        Camera camera = this.dragCamera;
        Area area = this.dragViewport;

        /* Guide-local -> camera-relative "world" of the preview scene. */
        Matrix4f localToWorld = new Matrix4f(camera.view).invert().mul(guide);

        if (Math.abs(localToWorld.determinant()) < 1e-12F)
        {
            return;
        }

        Matrix4f worldToLocal = new Matrix4f(localToWorld).invert();

        /* Mouse ray: origin = camera (0,0,0 camera-relative), direction from the projection. */
        Vector3f dirWorld = CameraUtils.getMouseDirection(
            camera.projection, camera.view,
            context.mouseX, context.mouseY,
            area.x, area.y, area.w, area.h
        );

        Vector3f o = worldToLocal.transformPosition(new Vector3f(0F, 0F, 0F));
        Vector3f d = worldToLocal.transformDirection(new Vector3f(dirWorld));

        if (d.lengthSquared() < EPSILON * EPSILON)
        {
            return;
        }

        if (HANDLE_RANGE.equals(this.dragHandle))
        {
            updateRange(o, d);
        }
        else
        {
            updateAngle(o, d, HANDLE_INNER.equals(this.dragHandle));
        }

        UISpotlightFormPanel activePanel = this.panel.get();

        if (activePanel != null)
        {
            activePanel.syncLightShape(this.dragForm);
        }
    }

    /**
     * Range handle: the cap-center disc slides along the guide's Z axis. New
     * range = Z of the closest point on that axis to the mouse ray.
     */
    private void updateRange(Vector3f o, Vector3f d)
    {
        float range = SpotGuideMath.range(o.x, o.y, o.z, d.x, d.y, d.z);

        if (Float.isFinite(range))
        {
            writeValue(range, this.dragForm.range);
        }
    }

    /**
     * Radius / inner radius handles: the rings live in the cap plane. Intersect
     * the ray with that plane; the radial distance to the axis maps back to the
     * cone angle via {@code angle = 2 * atan(radial / range)}.
     */
    private void updateAngle(Vector3f o, Vector3f d, boolean inner)
    {
        float angle = SpotGuideMath.angle(o.x, o.y, o.z, d.x, d.y, d.z,
            dragForm.range.get(), inner ? this.dragForm.radius.get() : 179F);

        if (Float.isFinite(angle))
        {
            writeValue(angle, inner ? this.dragForm.innerRadius : this.dragForm.radius);
        }
    }

    /**
     * Lands the dragged value: film drags write the target keyframe (the
     * per-frame applyProperties then propagates it into the rendered copy),
     * preview drags write the edited form's value directly.
     */
    private void writeValue(float value, ValueFloat directTarget)
    {
        if (this.filmKeyframes != null)
        {
            filmKeyframes.write(value, ((UIFilmController) this.dragHost).panel.getCursor());
        }
        else
        {
            directTarget.set(value);
        }
    }

    /**
     * True when the given form belongs to the replay currently selected in an
     * OPEN film editor — the state in which in-world guides and grab handles
     * are shown without the global show-guides setting.
     */
    /**
     * The film editor renders actors with COPIES of {@code replay.form}, so
     * identity checks against the replay are useless on the render side.
     * Instead we ride BBS's own selection signal: the film stencil pass picks
     * ONLY the selected replay per-bone ({@code increment=true}); the light
     * renderer calls {@link #markFilmSelected} from exactly that pass, and the
     * world pass shows guides for marked roots while the mark stays fresh.
     */
    private final Map<Form, Long> filmSelected = new WeakHashMap<>();

    private static final long FILM_MARK_TTL_MS = 300L;

    public void markFilmSelected(Form form)
    {
        Form root = FormUtils.getRoot(form);

        if (root != null)
        {
            filmSelected.put(root, System.currentTimeMillis());
        }
    }

    public boolean isFilmSelected(Form form)
    {
        /* Guides only in the replay editor — hide them the instant the user
         * switches to the camera editor or the replay actions timeline, even
         * while the mark is still fresh. */
        if (!isReplayEditorActive())
        {
            return false;
        }

        Form root = FormUtils.getRoot(form);
        Long mark = root == null ? null : this.filmSelected.get(root);

        return mark != null && System.currentTimeMillis() - mark < FILM_MARK_TTL_MS;
    }

    /**
     * The open film panel, or null when the film editor isn't the current screen.
     * Weakly cached so the per-frame lookups don't walk the dashboard panels.
     */
    private UIFilmPanel filmPanel()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        if (dashboard == null || UIScreen.getCurrentMenu() != dashboard)
        {
            return null;
        }

        UIFilmPanel film = this.filmPanelCache.get();

        if (film == null)
        {
            film = dashboard.getPanel(UIFilmPanel.class);
            filmPanelCache = new WeakReference<>(film);
        }

        return film;
    }

    /** The replay currently selected in the film editor's replays panel, if any. */
    private Replay selectedReplay()
    {
        UIFilmPanel film = filmPanel();

        return film == null ? null : film.replayEditor.getReplay();
    }

    /**
     * True only while the REPLAY editor is the active film editor and it isn't in
     * actions mode — the sole film context where the spotlight guides and grab
     * handles are allowed. In the camera editor, or the replay editor's actions
     * timeline, the same replay stays selected (so BBS keeps picking it), but the
     * guides must stay hidden and non-grabbable there.
     */
    public boolean isReplayEditorActive()
    {
        UIFilmPanel film = filmPanel();

        return film != null
            && film.replayEditor != null
            && film.replayEditor.isVisible()
            && film.replayEditor.getCategory() != UIReplaysEditor.ReplayCategory.ACTIONS;
    }

    public boolean isHandle(String bone)
    {
        return HANDLE_RADIUS.equals(bone) || HANDLE_INNER.equals(bone) || HANDLE_RANGE.equals(bone);
    }


}
