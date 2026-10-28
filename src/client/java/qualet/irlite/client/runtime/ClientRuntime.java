package qualet.irlite.client.runtime;

import qualet.irlite.client.diag.ProfileSession;
import qualet.irlite.client.forms.SpotGuideDrag;
import qualet.irlite.client.forms.WorldLightGuideOverlay;
import qualet.irlite.client.ui.patcher.PatcherController;
import qualet.irlite.domain.replay.ReplayFrameState;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;

/** Single bootstrap bridge for static engine callbacks. All session state has an instance owner. */
public final class ClientRuntime
{
    private static final class Holder
    {
        private static final ClientRuntime INSTANCE = new ClientRuntime();
    }

    private final ReplayFrameState<IEntity, Form> replays = new ReplayFrameState<>();
    private final SpotGuideDrag spotGuide = new SpotGuideDrag();
    private final WorldLightGuideOverlay guides = new WorldLightGuideOverlay();
    private final ProfileSession profiler = new ProfileSession();
    private final PatcherController patcher = new PatcherController();
    private final ResourceVersions resources = new ResourceVersions();
    private Object world;

    public static void install()
    {
        get();
    }

    public static ClientRuntime get()
    {
        return Holder.INSTANCE;
    }

    private ClientRuntime()
    {}

    public ReplayFrameState<IEntity, Form> replays()
    {
        return this.replays;
    }

    public SpotGuideDrag spotGuide()
    {
        return this.spotGuide;
    }

    public WorldLightGuideOverlay guides()
    {
        return this.guides;
    }

    public ResourceVersions resources()
    {
        return this.resources;
    }

    public PatcherController patcher()
    {
        return this.patcher;
    }

    public ProfileSession profiler()
    {
        return this.profiler;
    }

    public void beginFrame(Object world)
    {
        if (this.world != world)
        {
            this.reset();
            this.world = world;
        }
    }

    public void reset()
    {
        this.profiler.resetSession();
        this.replays.reset();
        this.spotGuide.reset();
        this.guides.beginFrame();
        this.world = null;
    }
}
