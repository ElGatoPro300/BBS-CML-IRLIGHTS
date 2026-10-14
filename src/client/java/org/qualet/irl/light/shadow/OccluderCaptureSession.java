package org.qualet.irl.light.shadow;

import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Render-thread capture session for the CML caster source. CML has no capture hook of
 * its own, so {@code RenderLayerCaptureMixin} diverts every {@code RenderLayer#draw}
 * issued while a session is open into a CPU copy of the built buffer and cancels the
 * draw. Everything the form emitters batch through the BBS provider ends there.
 */
public final class OccluderCaptureSession
{
    /** One diverted draw: its parameters and a native-order copy of the vertex data. */
    record Captured(BuiltBuffer.DrawParameters params, ByteBuffer data)
    {}

    private static List<Captured> active;

    private OccluderCaptureSession()
    {}

    static List<Captured> begin()
    {
        List<Captured> outer = active;

        active = new ArrayList<>();

        return outer;
    }

    /** Ends the current session, restoring {@code outer}; returns what it captured. */
    static List<Captured> end(List<Captured> outer)
    {
        List<Captured> captured = active;

        active = outer;

        return captured;
    }

    public static boolean isActive()
    {
        return active != null;
    }

    /** Copy and close {@code buffer}; called by the mixin only while {@link #isActive()}. */
    public static void divert(RenderLayer layer, BuiltBuffer buffer)
    {
        try (buffer)
        {
            ByteBuffer source = buffer.getBuffer();
            ByteBuffer copy = ByteBuffer.allocate(source.remaining()).order(ByteOrder.nativeOrder());

            copy.put(source.duplicate());
            copy.flip();
            active.add(new Captured(buffer.getDrawParameters(), copy));
        }
    }
}
