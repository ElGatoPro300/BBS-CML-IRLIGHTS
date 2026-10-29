"""Engine API adapters for existing opt-in fixtures; assertions remain shared."""
from pathlib import Path


def adapt_fixture(mc, source, output):
    if mc.startswith("26."):
        generated = Path(__file__).resolve().parents[1] / "build/source-bridge/official/fixtures" / source.name
        text = generated.read_text(encoding="utf-8").replace(".afterRender(", ".afterExtract(").replace("GuiGraphics", "GuiGraphicsExtractor")
        if source.name == "SoftTransparencyClientCheck.java":
            text = text.replace('getDeclaredMethod("iris$setupState", com.mojang.blaze3d.textures.GpuTextureView.class)', 'getDeclaredMethod("iris$setupState", java.util.HashMap.class, com.mojang.blaze3d.textures.GpuTextureView.class)')
            text = text.replace("apply.invoke(entity, (Object) null)", "apply.invoke(entity, new java.util.HashMap<>(), null)")
        if mc in {"26.2", "26.3"}:
            if source.name == "CasterDepthClientCheck.java":
                text = text.replace("                check();", """                int previousClip = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL45.GL_CLIP_DEPTH_MODE);
                double previousClear = org.lwjgl.opengl.GL11.glGetDouble(org.lwjgl.opengl.GL11.GL_DEPTH_CLEAR_VALUE);
                org.lwjgl.opengl.GL45.glClipControl(org.lwjgl.opengl.GL45.GL_LOWER_LEFT, org.lwjgl.opengl.GL45.GL_ZERO_TO_ONE);
                org.lwjgl.opengl.GL11.glClearDepth(0D);
                try
                {
                    check();
                }
                finally
                {
                    org.lwjgl.opengl.GL45.glClipControl(org.lwjgl.opengl.GL45.GL_LOWER_LEFT, previousClip);
                    org.lwjgl.opengl.GL11.glClearDepth(previousClear);
                }""")
            text = text.replace("._disableBlend()", "._disableBlend(0)").replace("._enableBlend()", "._enableBlend(0)")
            text = text.replace("client.getOverlay()", "client.gui.overlay()").replace("client.getMainRenderTarget()", "client.gameRenderer.mainRenderTarget()")
        if mc == "26.3":
            text = text.replace('getDeclaredMethod("iris$setupState", java.util.HashMap.class, com.mojang.blaze3d.textures.GpuTextureView.class)', 'getDeclaredMethod("iris$setupState", java.util.List.class)').replace("apply.invoke(entity, new java.util.HashMap<>(), null)", "apply.invoke(entity, java.util.List.of())")
            text = text.replace("com.mojang.blaze3d.opengl.", "com.mojang.renderpearl.backend.opengl.").replace("com.mojang.blaze3d.textures.GpuTextureView", "com.mojang.renderpearl.api.textures.GpuTextureView")
        adapted = output / source.name
        adapted.write_text(text, encoding="utf-8")
        return adapted
    if mc not in {"1.21.4", "1.21.11"}:
        return source
    text = source.read_text(encoding="utf-8")
    if mc == "1.21.4":
        for old, new in [("VertexSorter", "ProjectionType"), ("getVertexSorting()", "getProjectionType()"), ("ProjectionType.BY_Z", "ProjectionType.ORTHOGRAPHIC"), ("target.clear(false)", "target.clear()"), ("new SimpleFramebuffer(width, height, true, false)", "new SimpleFramebuffer(width, height, true)")]:
            text = text.replace(old, new)
    elif source.name == "ReplayFilmContextClientCheck.java":
        text = text.replace("import net.minecraft.client.gl.SimpleFramebuffer;", "import mchorse.bbs_mod.graphics.Framebuffer;\nimport mchorse.bbs_mod.graphics.texture.Texture;\nimport mchorse.bbs_mod.client.render.FormRenderCommands;\nimport mchorse.bbs_mod.client.hud.HudRenderScope;")
        text = text.replace("import com.mojang.blaze3d.systems.VertexSorter;", "")
        text = text.replace("SimpleFramebuffer", "Framebuffer")
        start = text.index("        draw.draw();")
        end = text.index("    private static void panels", start)
        text = text[:start] + """        FormRenderCommands.get().getHudGui().render(target, menu.width, menu.height, batcher ->
        {
            UIRenderingContext rendering = new UIRenderingContext(batcher.getContext());
            rendering.batcher = batcher;
            menu.context.setup(rendering);
            menu.renderMenu(rendering, -100, -100);
            rendering.executeRunnables();
        });
    }

""" + text[end:]
        start = text.index("        Matrix4f projection =")
        end = text.index("    @Override", start)
        text = text[:start] + """        Framebuffer target = new Framebuffer().deleteTextures();
        Texture texture = new Texture();
        texture.setSize(width, height);
        target.attach(texture, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0);
        UIBaseMenu menu = new UIBaseMenu() {};
        menu.context.setup(new UIRenderingContext(draw));
        menu.resize(width, height);
        try (HudRenderScope state = new HudRenderScope())
        {
            panels(menu, draw, target);
            contexts(menu, draw, target);
        }
        finally
        {
            target.delete();
            Framebuffer.bindMinecraft(client.getFramebuffer());
        }
    }

""" + text[end:]
    elif source.name == "SoftTransparencyClientCheck.java":
        text = text.replace("draw.draw();", "new mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D(draw).flush();")
        text = text.replace("GL30.glBindFramebuffer(", "com.mojang.blaze3d.opengl.GlStateManager._glBindFramebuffer(")
        text = text.replace("try (RenderPassState saved = new RenderPassState())", "try (RenderPassState saved = new RenderPassState(); mchorse.bbs_mod.graphics.PixelReadScope read = new mchorse.bbs_mod.graphics.PixelReadScope())")
        text = text.replace("RenderSystem.disableBlend()", "com.mojang.blaze3d.opengl.GlStateManager._disableBlend()")
        text = text.replace('ExtendedShader.class.getDeclaredMethod(System.getProperty("irlite.platform.shaderBlendMethod", "applyBlendModes"))', 'ExtendedShader.class.getDeclaredMethod("iris$setupState", com.mojang.blaze3d.textures.GpuTextureView.class)')
        text = text.replace("apply.invoke(entity)", "apply.invoke(entity, (Object) null)")
        text = text.replace('if (apply.getName().equals("bind"))', 'if (apply.getName().equals("iris$setupState"))')
        text = text.replace('getMethod("unbind")', 'getMethod("iris$clearState")')
    adapted = output / source.name
    adapted.write_text(text, encoding="utf-8")
    return adapted
