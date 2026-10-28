package qualet.irlite.client.diag;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Presentation-only adapter for collected performance lines. */
final class ProfileHud
{
    private ProfileHud()
    {}

    static void render(DrawContext ctx, List<String> lines, String sweepStatus)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.textRenderer == null)
        {
            return;
        }
        int y = 4;
        if (sweepStatus != null)
        {
            ctx.drawText(mc.textRenderer, sweepStatus, 4, y, 0xFFFFD080, true);
            y += 10;
        }
        for (String lineText : lines)
        {
            ctx.drawText(mc.textRenderer, lineText, 4, y, 0xFFE0E0E0, true);
            y += 10;
        }
    }
}
