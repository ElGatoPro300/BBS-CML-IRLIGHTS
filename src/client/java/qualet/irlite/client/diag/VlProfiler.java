package qualet.irlite.client.diag;

import qualet.irlite.client.runtime.ClientRuntime;

import net.minecraft.client.gui.DrawContext;

/** Stateless compatibility bridge for Core and Iris callbacks. */
public final class VlProfiler
{
    public static final String PASS_BAKE = "bake-head";
    public static final String PASS_VL = "deferred2";
    private VlProfiler()
    {}

    public static boolean isEnabled()
    {
        return ClientRuntime.get().profiler().isEnabled();
    }

    public static boolean isEnabledOrPending()
    {
        return ClientRuntime.get().profiler().isEnabledOrPending();
    }

    public static void toggle()
    {
        ClientRuntime.get().profiler().toggle();
    }

    public static void registerPassName(Object program, String name)
    {
        ClientRuntime.get().profiler().registerPassName(program, name);
    }

    public static String irisPassName(Object program)
    {
        return ClientRuntime.get().profiler().irisPassName(program);
    }

    public static void frameTick()
    {
        ClientRuntime.get().profiler().frameTick();
    }

    public static boolean detailedTimings()
    {
        return ClientRuntime.get().profiler().detailedTimings();
    }

    public static void invalidateFrame()
    {
        ClientRuntime.get().profiler().invalidateFrame();
    }

    public static void switchPass(String name)
    {
        ClientRuntime.get().profiler().switchPass(name);
    }

    public static void cpuSample(String name, long ns)
    {
        ClientRuntime.get().profiler().cpuSample(name, ns);
    }

    public static void counter(String key, int amount)
    {
        ClientRuntime.get().profiler().counter(key, amount);
    }

    public static void beginPass(String name)
    {
        ClientRuntime.get().profiler().beginPass(name);
    }

    public static void endPass()
    {
        ClientRuntime.get().profiler().endPass();
    }

    public static void overrideVlGlobals()
    {
        ClientRuntime.get().profiler().overrideVlGlobals();
    }

    public static void renderHud(DrawContext ctx)
    {
        ClientRuntime.get().profiler().renderHud(ctx);
    }

    static void log(String message)
    {
        ClientRuntime.get().profiler().log(message);
    }
    static void chat(String message)
    {
        ClientRuntime.get().profiler().chat(message);
    }
}
