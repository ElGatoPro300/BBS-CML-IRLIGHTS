import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import qualet.irlite.client.ui.forms.editors.panels.LightReplayWidgets;
import qualet.irlite.client.ui.forms.editors.panels.UILightEffectsPanel;
import qualet.irlite.client.ui.replays.ReplayFilmContext;
import qualet.irlite.client.ui.replays.UIReplaySelectionKeyframeFactory;
import qualet.irlite.forms.LightForm;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.forms.ValueReplaySelection;

/** Opt-in client regression: the Outline tab must also work outside a film editor. */
public final class ReplayFilmContextClientCheck implements ClientModInitializer
{
    private boolean tested;

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    private static void render(UIBaseMenu menu, DrawContext draw, SimpleFramebuffer target)
    {
        draw.draw();
        target.clear(false);
        target.beginWrite(true);
        UIRenderingContext rendering = new UIRenderingContext(draw);
        menu.context.setup(rendering);
        menu.renderMenu(rendering, -100, -100);
        rendering.executeRunnables();
        draw.draw();
    }

    private static void panels(UIBaseMenu menu, DrawContext draw, SimpleFramebuffer target)
    {
        for (LightForm form : new LightForm[] {new PointLightForm(), new SpotlightForm()})
        {
            UILightEffectsPanel<LightForm> outline = new UILightEffectsPanel<>(null, false);
            menu.main.add(outline);
            outline.set(0, 0, 450, 700);
            outline.resize();
            outline.startEdit(form);
            for (boolean enabled : new boolean[] {false, true})
            {
                form.effects.customOutline.set(enabled);
                render(menu, draw, target);
                for (UIButton button : outline.getChildren(UIButton.class))
                {
                    if (button.label.get().equals("Choose outlined replays...")) button.callback.accept(button);
                }
                render(menu, draw, target);
            }
            require(form.effects.outlineReplays.get().isEmpty(), "Unavailable picker changed the saved selection");
            outline.removeFromParent();
        }
        System.out.println("PASS: actual point/spot Outline panels render outside films with own settings enabled/disabled");
    }

    private static void contexts(UIBaseMenu menu, DrawContext draw, SimpleFramebuffer target)
    {
        UIElement owner = new UIElement();
        require(ReplayFilmContext.find(null) == null, "Null owner");
        require(ReplayFilmContext.find(owner) == null, "Detached owner");
        UIElement nested = new UIElement();
        menu.main.add(nested);
        nested.add(owner);
        require(ReplayFilmContext.find(owner) == null, "Attached non-film hierarchy");
        ValueBoolean enabled = new ValueBoolean("enabled", true);
        ValueReplaySelection selected = new ValueReplaySelection("selected");
        LightReplayWidgets widgets = new LightReplayWidgets(owner, "", "", () -> enabled, () -> selected);
        for (UIElement element : widgets.elements()) owner.add(element);
        owner.set(0, 0, 200, 100);
        owner.resize();
        render(menu, draw, target);
        UIButton choose = (UIButton) widgets.elements()[2];
        choose.callback.accept(choose);
        require(selected.get().isEmpty() && enabled.get(), "Missing film changed saved filter values");
        UIReplaySelectionKeyframeFactory keyframe = new UIReplaySelectionKeyframeFactory(
            new Keyframe<>("0", KeyframeFactories.STRING, 0F, ""), new UIKeyframes(null));
        owner.add(keyframe);
        for (UIButton button : keyframe.getChildren(UIButton.class))
        {
            if (button.label.get().equals("Choose replays...")) button.callback.accept(button);
        }
        keyframe.removeFromParent();
        /* This title-screen fixture has no player camera; the real UI hierarchy is still constructed. */
        UIDashboard dashboard = new UIDashboard()
        {
            @Override
            public void copyCurrentEntityCamera()
            {}
        };
        UIFilmPanel film = dashboard.getPanel(UIFilmPanel.class);
        film.removeFromParent();
        menu.main.add(film);
        owner.removeFromParent();
        film.add(owner);
        require(ReplayFilmContext.find(owner) == film, "Ancestor film was not found");
        owner.removeFromParent();
        menu.overlay.add(owner);
        require(ReplayFilmContext.find(owner) == film, "Overlay fallback lost the menu film");
        film.removeFromParent();
        require(ReplayFilmContext.find(owner) == null, "Removed film remained available");
        owner.removeFromParent();
        nested.removeFromParent();
        System.out.println("PASS: detached/non-film trees, unavailable widget/keyframe picker, ancestor film and overlay fallback");
    }

    private static void check(DrawContext draw)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        double scale = client.getWindow().getScaleFactor();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorter = RenderSystem.getVertexSorting();
        SimpleFramebuffer target = new SimpleFramebuffer(width, height, true, false);
        UIBaseMenu menu = new UIBaseMenu() {};
        menu.context.setup(new UIRenderingContext(draw));
        menu.resize(width, height);
        try
        {
            client.getWindow().setScaleFactor(1D);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0F, width, height, 0F, 1000F, 21000F), VertexSorter.BY_Z);
            panels(menu, draw, target);
            contexts(menu, draw, target);
        }
        finally
        {
            target.delete();
            client.getWindow().setScaleFactor(scale);
            RenderSystem.setProjectionMatrix(projection, sorter);
            client.getFramebuffer().beginWrite(true);
        }
    }

    @Override
    public void onInitializeClient()
    {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenEvents.afterRender(screen).register((current, draw, x, y, delta) ->
        {
            if (this.tested || client.getOverlay() != null) return;
            this.tested = true;
            try
            {
                check(draw);
                System.out.println("PASS: replay film context client regression");
            }
            catch (Throwable error)
            {
                System.err.println("FAIL: replay film context client regression");
                error.printStackTrace();
            }
            finally
            {
                client.scheduleStop();
            }
        }));
    }
}
