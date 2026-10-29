package qualet.irlite.platform;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.ImmediateDraw;
import mchorse.bbs_mod.graphics.RenderPassState;
import mchorse.bbs_mod.graphics.ShaderDrawScope;
import mchorse.bbs_mod.utils.colors.Color;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Mapping-sensitive drawing operations selected by the target manifest. */
public final class RenderApi
{
    private RenderApi()
    {}

    public static Identifier identifier(String namespace, String path)
    {
        return Identifier.of(namespace, path);
    }

    public static BufferBuilder begin(VertexFormat format)
    {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, format);
    }

    public static void colorVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a)
    {
        builder.vertex(matrix, x, y, z).color(r, g, b, a);
    }

    public static void textureVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float u, float v, float r, float g, float b)
    {
        builder.vertex(matrix, x, y, z).texture(u, v).color(r, g, b, 1F);
    }

    public static void pushModelView()
    {
        RenderSystem.getModelViewStack().pushMatrix();
    }

    public static void popModelView()
    {
        RenderSystem.getModelViewStack().popMatrix();
    }

    public static void modelView(Matrix4f matrix)
    {
        RenderSystem.getModelViewStack().set(matrix);
    }

    public static void colorShader()
    {
        GlStateManager._glUseProgram(BBSRendering.getImmediateProgram(Draw.getPositionColorNoDepthLayer().getRenderPipeline()).getGlRef());
    }

    public static void textureShader()
    {
        GlStateManager._glUseProgram(BBSRendering.getImmediateProgram(BBSShaders.imageOverlayPipeline).getGlRef());
    }

    public static void pickerShader()
    {
        GlStateManager._glUseProgram(BBSShaders.getPickerModelsProgram().getGlRef());
    }

    public static void restoreShader(ShaderProgram shader)
    {
        GlStateManager._glUseProgram(shader == null ? 0 : shader.getGlRef());
    }

    public static void projection(Matrix4f matrix)
    {
        BBSRendering.setProjectionMatrix(matrix, ProjectionType.PERSPECTIVE);
    }

    public static final class ProjectionState implements AutoCloseable
    {
        private final Matrix4f matrix = new Matrix4f(BBSRendering.getProjectionMatrix());
        private final ProjectionType sorting = RenderSystem.getProjectionType();

        @Override
        public void close()
        {
            BBSRendering.setProjectionMatrix(this.matrix, this.sorting);
        }
    }

    public static Matrix4f projectionMatrix()
    {
        return BBSRendering.getProjectionMatrix();
    }

    public static void bindMain()
    {
        Framebuffer.bindMinecraft(MinecraftClient.getInstance().getFramebuffer());
    }

    public static void drawColor(BufferBuilder builder, boolean stencil)
    {
        ImmediateDraw.draw(builder.end(), Draw.getPositionColorNoDepthLayer().getRenderPipeline(), BBSRendering.getImmediateProgram(Draw.getPositionColorNoDepthLayer().getRenderPipeline()), true);
    }

    public static void drawTextured(BufferBuilder builder)
    {
        ShaderProgram shader = BBSRendering.getImmediateProgram(BBSShaders.imageOverlayPipeline);
        BBSShaders.withProgram(shader, bound ->
        {
            int location = GL20.glGetUniformLocation(bound.getGlRef(), "BlendMode");
            if (location >= 0) GL20.glUniform1i(location, 0);
        });
        ImmediateDraw.draw(builder.end(), BBSShaders.imageOverlayPipeline, shader, true);
    }

    public static final class ColorState implements AutoCloseable
    {
        private final float[] color = ShaderDrawScope.getColorArray();

        @Override
        public void close()
        {
            ShaderDrawScope.setColor(this.color[0], this.color[1], this.color[2], this.color[3]);
        }
    }

    public static void enableBlend()
    {
        GlStateManager._enableBlend();
    }

    public static void disableBlend()
    {
        GlStateManager._disableBlend();
    }

    public static void defaultBlendFunc()
    {
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableDepthTest()
    {
        GlStateManager._enableDepthTest();
    }

    public static void disableDepthTest()
    {
        GlStateManager._disableDepthTest();
    }

    public static void enableCull()
    {
        GlStateManager._enableCull();
    }

    public static void disableCull()
    {
        GlStateManager._disableCull();
    }

    public static void depthMask(boolean value)
    {
        GlStateManager._depthMask(value);
    }

    public static void setShaderColor(float r, float g, float b, float a)
    {
        ShaderDrawScope.setColor(r, g, b, a);
    }

    public static Vec3d cameraPosition(Camera camera)
    {
        return camera.getCameraPos();
    }

    public static void pickBox(FormRenderingContext context, Color color, Runnable configure)
    {
        int index = context.getPickingIndex();
        try (RenderPassState state = new RenderPassState(); ColorState colorState = new ColorState())
        {
            disableBlend();
            setShaderColor(1F, 1F, 1F, 1F);
            BufferBuilder builder = begin(VertexFormats.POSITION_COLOR);
            Draw.fillBox(builder, context.stack, 0F, 0F, 0F, 0.5F, 0.5F, 0.5F,
                (index & 255) / 255F, ((index >> 8) & 255) / 255F, ((index >> 16) & 255) / 255F, 1F);
            drawColor(builder, false);
        }
    }

    public static Camera camera()
    {
        return MinecraftClient.getInstance().gameRenderer.getCamera();
    }

    public static Screen screen()
    {
        return MinecraftClient.getInstance().currentScreen;
    }

    public static Matrix4f modelViewMatrix()
    {
        return new Matrix4f(RenderSystem.getModelViewStack());
    }

    public static void flushEntityBuffers()
    {
        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw();
    }

    public static void rotateYaw(MatrixStack stack, float yaw)
    {
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
    }

    public static void classicDepth()
    {
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(true);
    }

    public static int fullLight()
    {
        return LightmapTextureManager.pack(15, 15);
    }

    public static void registerProfilerHud()
    {
        HudRenderCallback.EVENT.register((context, delta) ->
            qualet.irlite.client.diag.VlProfiler.renderHud((message, x, y, color) ->
                context.drawText(MinecraftClient.getInstance().textRenderer, message, x, y, color, true)));
    }

    public static void chat(String message)
    {
        MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal(message));
    }
}
