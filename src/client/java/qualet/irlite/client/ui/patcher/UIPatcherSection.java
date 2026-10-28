package qualet.irlite.client.ui.patcher;

import qualet.irlite.client.runtime.ClientRuntime;

import mchorse.bbs_mod.ui.framework.elements.UIScrollView;

/** Stateless mount point used by the settings registration callback. */
public final class UIPatcherSection
{
    private UIPatcherSection()
    {}

    public static void append(UIScrollView options, Runnable rebuild)
    {
        ClientRuntime.get().patcher().append(options, rebuild);
    }
}
