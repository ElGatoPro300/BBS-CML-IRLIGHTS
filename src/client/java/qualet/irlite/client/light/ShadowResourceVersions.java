package qualet.irlite.client.light;

import qualet.irlite.client.runtime.ClientRuntime;

/** Stateless bridge used by texture and VAO mixins, including early asset uploads. */
public final class ShadowResourceVersions
{
    private ShadowResourceVersions()
    {}

    public static long next()
    {
        return ClientRuntime.get().resources().next();
    }

    public static long reloadVersion()
    {
        return ClientRuntime.get().resources().reloadVersion();
    }

    public static void reloaded()
    {
        ClientRuntime.get().resources().reloaded();
    }
}
