package qualet.irlite.client.diag;

import java.util.List;

/** Presentation-only adapter for collected performance lines. */
final class ProfileHud
{
    private ProfileHud()
    {}

    static void render(VlProfiler.HudCanvas ctx, List<String> lines, String sweepStatus)
    {
        int y = 4;
        if (sweepStatus != null)
        {
            ctx.text(sweepStatus, 4, y, 0xFFFFD080);
            y += 10;
        }
        for (String lineText : lines)
        {
            ctx.text(lineText, 4, y, 0xFFE0E0E0);
            y += 10;
        }
    }
}
