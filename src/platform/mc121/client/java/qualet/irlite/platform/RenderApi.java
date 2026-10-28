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
}
