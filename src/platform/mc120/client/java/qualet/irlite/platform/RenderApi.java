package qualet.irlite.platform;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.Identifier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

/** Mapping-sensitive drawing operations selected by the target manifest. */
public final class RenderApi
{
    private RenderApi()
    {}

    public static Identifier identifier(String namespace, String path)
    {
        return new Identifier(namespace, path);
    }

    public static BufferBuilder begin(VertexFormat format)
    {
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, format);
        return builder;
    }

    public static void colorVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float a)
    {
        builder.vertex(matrix, x, y, z).color(r, g, b, a).next();
    }

    public static void textureVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float u, float v, float r, float g, float b)
    {
        builder.vertex(matrix, x, y, z).texture(u, v).color(r, g, b, 1F).next();
    }

    public static void pushModelView()
    {
        RenderSystem.getModelViewStack().push();
    }

    public static void popModelView()
    {
        RenderSystem.getModelViewStack().pop();
    }

    public static void modelView(Matrix4f matrix)
    {
        RenderSystem.getModelViewStack().peek().getPositionMatrix().set(matrix);
        RenderSystem.applyModelViewMatrix();
    }
}
