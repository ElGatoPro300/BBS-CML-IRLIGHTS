package qualet.irlite.client.render;

import qualet.irlite.client.diag.VlProfiler;
import qualet.irlite.client.light.LightCollector;
import qualet.irlite.client.light.ReplayOutlineContext;
import qualet.irlite.client.runtime.ClientRuntime;

import org.qualet.irl.light.FramePipeline;
import org.qualet.irl.light.iris.IrisShadersState;

import net.minecraft.client.MinecraftClient;

/** Shared frame orchestration; version-specific mixins only supply the partial tick. */
public final class LightFramePipeline
{
    private LightFramePipeline()
    {}

    public static void begin(float tickDelta)
    {
        ClientRuntime.get().beginFrame(MinecraftClient.getInstance().world);
        ClientRuntime.get().guides().beginFrame();
        // Dev VL profiler (-Dirlite.profileVl=true): the shadow bake below runs
        // strictly before the Iris pass sequence, so its GL_TIME_ELAPSED bracket
        // never nests with the per-pass brackets. collect/prioritize inside
        // frame() issue no GL, so the bracket measures bake GPU work only. The
        // core-side ShadowBakeProbe (installed in IrliteClient) switches this
        // bracket to bake-spot/-spot-pyr/-spot-evsm/-point/-point-pyr/
        // -point-evsm/-tail siblings at the bakeInner seams; the endPass below
        // closes whichever segment is open (the tail, or the head when the
        // bake early-returned).
        VlProfiler.frameTick();
        // Replay tags first: the light collection below resolves each light's replay lists against them.
        ReplayOutlineContext.beginFrame();
        VlProfiler.beginPass(VlProfiler.PASS_BAKE);
        long pipelineT0 = System.nanoTime();
        try
        {
            FramePipeline.frame(tickDelta, IrisShadersState::shadersDisabled, LightCollector::collect, () -> {});
        }
        catch (RuntimeException | Error e)
        {
            VlProfiler.invalidateFrame();
            throw e;
        }
        finally
        {
            VlProfiler.cpuSample("pipeline", System.nanoTime() - pipelineT0);
            VlProfiler.endPass();
        }
    }

    public static void upload()
    {
        long started = System.nanoTime();
        FramePipeline.uploadIfPending();
        VlProfiler.cpuSample("upload", System.nanoTime() - started);
    }
}
