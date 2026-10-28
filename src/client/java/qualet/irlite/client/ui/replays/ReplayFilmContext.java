package qualet.irlite.client.ui.replays;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import java.util.List;

/** Resolves a replay editor's film, including form palettes opened outside the timeline. */
public final class ReplayFilmContext
{
    private ReplayFilmContext()
    {}

    public static UIFilmPanel find(UIElement owner)
    {
        UIContext context = owner == null ? null : owner.getContext();

        if (context == null)
        {
            return null;
        }

        for (UIElement parent = owner.getParent(); parent != null; parent = parent.getParent())
        {
            if (parent instanceof UIFilmPanel panel)
            {
                return panel;
            }
        }

        List<UIFilmPanel> panels = context.menu.main.getChildren(UIFilmPanel.class);

        return panels.isEmpty() ? null : panels.get(0);
    }
}
