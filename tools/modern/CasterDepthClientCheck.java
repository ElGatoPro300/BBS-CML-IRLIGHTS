import org.qualet.irl.light.shadow.CasterType;
import org.qualet.irl.light.shadow.IRLiteBbsCasterSource;
import org.qualet.irl.light.shadow.ShadowRenderer;
import org.qualet.irl.light.shadow.SpotlightDepthAtlas;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.PixelReadScope;
import mchorse.bbs_mod.graphics.RenderPassState;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.models.mineimator.MineimatorModelReader;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.block.Blocks;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import net.irisshaders.iris.Iris;

import org.joml.Matrix4f;

import com.mojang.blaze3d.opengl.GlStateManager;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

/** Native depth pixels prove that the adapter submits animated CML geometry to Core. */
public final class CasterDepthClientCheck implements ClientModInitializer
{
    private static final String MODEL = "__irl_port_plane";
    private static final Link TEXTURE = Link.assets("textures/__irl_port_plane.png");
    private boolean tested;

    private static void require(boolean value, String message)
    {
        if (!value) throw new AssertionError(message);
    }

    private static long depth(Form form, float offset) throws Exception
    {
        StubEntity replay = new StubEntity();
        replay.setForm(form);
        form.transform.get().translate.x = offset;
        long failures = ShadowRenderer.casterFailures();
        int clip = GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE);
        double clear = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        ShadowRenderer.beginBake();
        ShadowRenderer.beginSpot(0, 0D, 0D, 3D, 0F, 0F, -1F, 16F, 60F, false, true);
        try
        {
            ShadowRenderer.beginCasterBatch();
            Matrix4f view = ShadowRenderer.currentViewMatrix();
            view.zero();
            require(!ShadowRenderer.currentViewMatrix().equals(view), "Core exposed mutable pass matrices");
            ShadowRenderer.emitCaster(new IRLiteBbsCasterSource(), replay, CasterType.REPLAY, 0F);
            ShadowRenderer.endCasterBatch();
        }
        finally
        {
            ShadowRenderer.endPass();
        }
        require(GL11.glGetInteger(GL45.GL_CLIP_DEPTH_MODE) == clip, "Core leaked clip depth mode");
        require(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE) == clear, "Core leaked depth clear value");
        require(ShadowRenderer.casterFailures() == failures, "Core recovered an unexpected caster failure");
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, SpotlightDepthAtlas.getFboId(false));
        int size = SpotlightDepthAtlas.tileSizePx(0);
        FloatBuffer values = BufferUtils.createFloatBuffer(size * size);
        try (PixelReadScope scope = new PixelReadScope())
        {
            GL11.glReadPixels(SpotlightDepthAtlas.tilePixelX(0), SpotlightDepthAtlas.tilePixelY(0), size, size,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, values);
        }
        int count = 0;
        long hash = 1L;
        for (int i = 0; i < values.capacity(); i++)
        {
            float value = values.get(i);
            if (value < 0.9999F)
            {
                require(Float.isFinite(value) && value >= 0F, "Invalid shadow depth");
                float expected = 16F / (16F - 0.05F) * (1F - 0.05F / 3F);
                require(form instanceof BlockForm || Math.abs(value - expected) < 0.0001F, "Caster depth disagrees with Core projection: " + value);
                count++;
                hash = 31L * hash + i;
            }
        }
        require(count > 40, "Real CML caster did not write depth: " + count);
        require(GL11.glGetError() == GL11.GL_NO_ERROR, "Native depth path produced an OpenGL error");
        System.out.println("CHECKED: caster depth pixels=" + count + " offset=" + offset);
        return hash;
    }

    private static void adapters()
    {
        try (RenderPassState state = new RenderPassState();
             qualet.irlite.platform.RenderApi.ProjectionState projection = new qualet.irlite.platform.RenderApi.ProjectionState();
             qualet.irlite.platform.RenderApi.ColorState color = new qualet.irlite.platform.RenderApi.ColorState())
        {
            Texture texture = new Texture();
            texture.setSize(32, 32);
            Framebuffer target = new Framebuffer().deleteTextures();
            target.attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            qualet.irlite.platform.RenderApi.pushModelView();
            try
            {
                target.bind();
                GL11.glViewport(0, 0, 32, 32);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                qualet.irlite.platform.RenderApi.disableDepthTest();
                qualet.irlite.platform.RenderApi.disableCull();
                qualet.irlite.platform.RenderApi.modelView(new Matrix4f());
                qualet.irlite.platform.RenderApi.projection(new Matrix4f().ortho(-1F, 1F, -1F, 1F, -1F, 1F));
                GL11.glClearColor(0F, 0F, 0F, 0F);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                FormRenderingContext context = new FormRenderingContext()
                {
                    @Override
                    public int getPickingIndex()
                    {
                        return 0x563412;
                    }
                };
                context.stack = new MatrixStack();
                context.stack.translate(-0.25F, -0.25F, 0F);
                qualet.irlite.platform.RenderApi.pickBox(context, new Color().set(0x10FF0000), () -> {});
                ByteBuffer pixel = BufferUtils.createByteBuffer(4);
                try (PixelReadScope read = new PixelReadScope())
                {
                    GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                }
                require((pixel.get(0) & 255) == 0x12 && (pixel.get(1) & 255) == 0x34 && (pixel.get(2) & 255) == 0x56,
                    "Filled pick volume lost exact object ID at its center");
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Picking produced an OpenGL error");
                System.out.println("CHECKED: filled light picking volume and exact center ID");
                Pixels icon = Pixels.fromSize(1, 1);
                Texture sample = new Texture();
                try
                {
                    icon.setColor(0, 0, new Color().set(0xFF4080C0));
                    icon.rewindBuffer();
                    sample.bind(0);
                    sample.setFilter(GL11.GL_NEAREST);
                    sample.uploadTexture(icon);
                    GlStateManager._bindTexture(sample.id);
                    qualet.irlite.platform.RenderApi.setShaderColor(1F, 1F, 1F, 1F);
                    BufferBuilder builder = qualet.irlite.platform.RenderApi.begin(VertexFormats.POSITION_TEXTURE_COLOR);
                    float[][] vertices = {{-1F, -1F}, {1F, -1F}, {1F, 1F}, {-1F, -1F}, {1F, 1F}, {-1F, 1F}};
                    for (float[] vertex : vertices)
                    {
                        qualet.irlite.platform.RenderApi.textureVertex(builder, new Matrix4f(), vertex[0], vertex[1], 0F, 0.5F, 0.5F, 1F, 1F, 1F);
                    }
                    qualet.irlite.platform.RenderApi.disableBlend();
                    qualet.irlite.platform.RenderApi.drawTextured(builder);
                    try (PixelReadScope read = new PixelReadScope())
                    {
                        pixel.clear();
                        GL11.glReadPixels(16, 16, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                    }
                    require((pixel.get(0) & 255) == 0x40 && (pixel.get(1) & 255) == 0x80 && (pixel.get(2) & 255) == 0xC0,
                        "Native textured light icon lost its sampled color: " + (pixel.get(0) & 255) + "," + (pixel.get(1) & 255) + "," + (pixel.get(2) & 255));
                    System.out.println("CHECKED: native textured icon pixels");
                }
                finally
                {
                    icon.delete();
                    sample.delete();
                }
            }
            finally
            {
                qualet.irlite.platform.RenderApi.popModelView();
                target.delete();
            }
        }
    }

    private static void check() throws Exception
    {
        Texture texture = new Texture();
        ModelInstance instance = null;
        try
        {
            Pixels pixels = Pixels.fromSize(16, 16);
            try
            {
                for (int x = 0; x < 16; x++)
                {
                    for (int y = 0; y < 16; y++)
                    {
                        pixels.setColor(x, y, new Color().set(x >= 4 && x < 12 && y >= 4 && y < 12 ? 0xFFFFFFFF : 0));
                    }
                }
                pixels.rewindBuffer();
                GlStateManager._bindTexture(texture.id);
                texture.setFilter(GL11.GL_NEAREST);
                texture.uploadTexture(pixels);
            }
            finally
            {
                pixels.delete();
            }
            BBSModClient.getTextures().textures.put(TEXTURE, texture);
            MolangParser parser = new MolangParser();
            String json = "{\"texture_size\":[16,16],\"parts\":[{\"name\":\"test\",\"shapes\":[{\"type\":\"plane\",\"from\":[-12,-12,0],\"to\":[12,12,0]}]}]}";
            instance = new ModelInstance(MODEL, MineimatorModelReader.read(json.getBytes(StandardCharsets.UTF_8), parser, null).model(), new Animations(parser), TEXTURE);
            instance.culling = false;
            BBSModClient.getModels().models.put(MODEL, instance);
            ModelForm form = new ModelForm();
            form.model.set(MODEL);
            form.lighting.set(0F);
            long first = depth(form, 0F);
            long moved = depth(form, 0.5F);
            require(first != moved, "Moving a caster did not move its shadow");
            BlockForm block = new BlockForm();
            block.blockState.set(Blocks.STONE.getDefaultState());
            long blockFirst = depth(block, 0F);
            long blockMoved = depth(block, 0.5F);
            require(blockFirst != blockMoved, "Native block provider did not move its shadow");
            AnchorForm parent = new AnchorForm();
            BodyPart child = new BodyPart("");
            child.setForm(form);
            parent.parts.addBodyPart(child);
            require(depth(parent, 0F) == moved, "Nested body part lost its actual caster transform");
            System.out.println("PASS: native CML caster depth, movement, nested parts, defensive Core snapshots and balanced pass state");
        }
        finally
        {
            BBSModClient.getModels().models.remove(MODEL);
            BBSModClient.getTextures().textures.remove(TEXTURE);
            if (instance != null) instance.delete();
            texture.delete();
            SpotlightDepthAtlas.delete();
            ShadowRenderer.releaseScratch();
        }
    }

    @Override
    public void onInitializeClient()
    {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> ScreenEvents.afterRender(screen).register((current, draw, x, y, delta) ->
        {
            if (this.tested || client.getOverlay() != null) return;
            this.tested = true;
            boolean shaders = Iris.getIrisConfig().areShadersEnabled();
            Iris.getIrisConfig().setShadersEnabled(false);
            Iris.getPipelineManager().destroyPipeline();
            try (RenderPassState state = new RenderPassState())
            {
                adapters();
                SpotlightDepthAtlas.setTileSize(256);
                check();
            }
            catch (Throwable error)
            {
                System.err.println("FAIL: native CML caster depth");
                error.printStackTrace();
            }
            finally
            {
                Iris.getIrisConfig().setShadersEnabled(shaders);
                Framebuffer.bindMinecraft(client.getFramebuffer());
                client.scheduleStop();
            }
        }));
    }
}
