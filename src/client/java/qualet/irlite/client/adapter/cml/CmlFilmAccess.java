package qualet.irlite.client.adapter.cml;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIScreen;

import net.minecraft.client.MinecraftClient;

/** Existing scanner/caster screen policies share one engine-facing lookup. */
public final class CmlFilmAccess
{
    private CmlFilmAccess()
    {}

    public static FilmEditorController activeEditor(boolean requireBbsScreen)
    {
        try
        {
            MinecraftClient client = MinecraftClient.getInstance();

            if (client == null || client.currentScreen == null
                || (requireBbsScreen && !(client.currentScreen instanceof UIScreen)))
            {
                return null;
            }

            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard == null || !(dashboard.getPanels().panel instanceof UIFilmPanel film))
            {
                return null;
            }

            UIFilmController controller = film.getController();

            return controller == null ? null : controller.editorController;
        }
        catch (RuntimeException | LinkageError failure)
        {
            return null;
        }
    }
}
