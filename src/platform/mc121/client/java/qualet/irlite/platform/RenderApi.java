package qualet.irlite.platform;

import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.utils.colors.Color;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

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
        RenderSystem.applyModelViewMatrix();
    }

    public static void colorShader()
    {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }

    public static void textureShader()
    {
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
    }

    public static void pickerShader()
    {
        RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
    }

    public static void restoreShader(ShaderProgram shader)
    {
        RenderSystem.setShader(() -> shader);
    }

    public static void projection(Matrix4f matrix)
    {
        RenderSystem.setProjectionMatrix(matrix, VertexSorter.BY_Z);
    }

    public static final class ProjectionState implements AutoCloseable
    {
        private final Matrix4f matrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final VertexSorter sorting = RenderSystem.getVertexSorting();

        @Override
        public void close()
        {
            RenderSystem.setProjectionMatrix(this.matrix, this.sorting);
        }
    }

    public static int overlay(LivingEntity entity, float tickDelta)
    {
        return LivingEntityRenderer.getOverlay(entity, 0F);
    }

    public static void renderEntity(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack stack, VertexConsumerProvider.Immediate buffers, int light)
    {
        MinecraftClient.getInstance().getEntityRenderDispatcher().render(entity, x, y, z, yaw, tickDelta, stack, buffers, light);
    }

    public static Matrix4f projectionMatrix()
    {
        return RenderSystem.getProjectionMatrix();
    }

    public static void bindMain()
    {
        MinecraftClient.getInstance().getFramebuffer().beginWrite(true);
    }

    public static void drawColor(BufferBuilder builder, boolean stencil)
    {
        colorShader();
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public static void drawTextured(BufferBuilder builder)
    {
        textureShader();
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public static final class ColorState implements AutoCloseable
    {
        private final float[] color = RenderSystem.getShaderColor().clone();

        @Override
        public void close()
        {
            RenderSystem.setShaderColor(this.color[0], this.color[1], this.color[2], this.color[3]);
        }
    }

    public static void enableBlend()
    {
        RenderSystem.enableBlend();
    }

    public static void disableBlend()
    {
        RenderSystem.disableBlend();
    }

    public static void defaultBlendFunc()
    {
        RenderSystem.defaultBlendFunc();
    }

    public static void enableDepthTest()
    {
        RenderSystem.enableDepthTest();
    }

    public static void disableDepthTest()
    {
        RenderSystem.disableDepthTest();
    }

    public static void enableCull()
    {
        RenderSystem.enableCull();
    }

    public static void disableCull()
    {
        RenderSystem.disableCull();
    }

    public static void depthMask(boolean value)
    {
        RenderSystem.depthMask(value);
    }

    public static void setShaderColor(float r, float g, float b, float a)
    {
        RenderSystem.setShaderColor(r, g, b, a);
    }

    public static Vec3d cameraPosition(Camera camera)
    {
        return camera.getPos();
    }

    public static void pickBox(FormRenderingContext context, Color color, Runnable configure)
    {
        CustomVertexConsumerProvider.hijackVertexFormat(layer ->
        {
            configure.run();
            pickerShader();
        });
        try
        {
            Draw.renderBox(context.stack, 0D, 0D, 0D, 0.5D, 0.5D, 0.5D, color.r, color.g, color.b);
        }
        finally
        {
            CustomVertexConsumerProvider.clearRunnables();
            enableDepthTest();
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
