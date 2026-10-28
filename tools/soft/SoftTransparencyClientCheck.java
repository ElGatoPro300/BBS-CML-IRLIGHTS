import qualet.irlite.client.light.ReplayOutlineContext;

import org.qualet.irl.light.shadow.ShadowBakeState;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.rendering.soft.SoftDrawEntry;
import mchorse.bbs_mod.client.rendering.soft.SoftDrawQueue;
import mchorse.bbs_mod.client.rendering.soft.SoftEntryRunner;
import mchorse.bbs_mod.graphics.RenderPassState;
import mchorse.bbs_mod.rendering.soft.SoftBatch;
import mchorse.bbs_mod.rendering.soft.SoftDrawSpec;
import mchorse.bbs_mod.rendering.soft.SoftSortKey;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BufferBlendOverride;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

/** GPU regression against CML's actual queues and Iris's real indexed blend locks. */
public final class SoftTransparencyClientCheck implements ClientModInitializer
{
    private boolean tested;
    private static int failures;
    private static Method setTag;

    private static void check(boolean condition, String message)
    {
        if (!condition)
        {
            failures++;
            System.err.println("FAIL: " + message);
        }
    }

    private static int shader(int type, String source)
    {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) throw new AssertionError(GL20.glGetShaderInfoLog(id));
        return id;
    }

    private static void tag(int id)
    {
        try { setTag.invoke(null, id); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private static void gpu(String mode, Runnable applyShaderBlend, int replaySlot) throws Exception
    {
        try (RenderPassState saved = new RenderPassState())
        {
            int fbo = GL30.glGenFramebuffers();
            int[] textures = new int[8];
            int vao = GL30.glGenVertexArrays();
            int program = GL20.glCreateProgram();
            int vertex = shader(GL20.GL_VERTEX_SHADER, "#version 150\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}");
            int fragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 150\nuniform float Alpha;uniform float Tag;out vec4 Color;out vec4 Replay;void main(){Color=vec4(1,0,0,Alpha);Replay=vec4(Tag,0,0,1);}");
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL30.glBindFragDataLocation(program, 0, "Color");
            GL30.glBindFragDataLocation(program, replaySlot, "Replay");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) throw new AssertionError(GL20.glGetProgramInfoLog(program));
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            int[] attachments = new int[8];
            for (int i = 0; i < textures.length; i++)
            {
                textures[i] = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, 8, 8, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + i, GL11.GL_TEXTURE_2D, textures[i], 0);
                attachments[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
            }
            GL20.glDrawBuffers(attachments);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new AssertionError("Incomplete fixture framebuffer");
            GL11.glViewport(0, 0, 8, 8);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColorMask(true, true, true, true);
            try
            {
                for (SoftDrawQueue queue : List.of(BBSModClient.getSoftTransparency().getQueue(), BBSModClient.getSoftTransparency().getPostComposite()))
                {
                    for (float alpha : new float[] {0.01F, 0.075F, 0.1F, 0.25F, 0.5F, 0.75F, 0.998F, 1F})
                    {
                        BlendModeOverride.restore();
                        RenderSystem.disableBlend();
                        GL11.glDisable(GL11.GL_BLEND);
                        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[] {0F, 0F, 1F, 1F});
                        GL30.glClearBufferfv(GL11.GL_COLOR, replaySlot, new float[] {0F, 0F, 0F, 0F});
                        new BufferBlendOverride(replaySlot, null).apply();
                        queue.beginFrame();
                        tag(17);
                        SoftDrawSpec spec = SoftDrawSpec.forAlpha(alpha, SoftSortKey.of(0D, 4D));
                        queue.submit(spec, () ->
                        {
                            applyShaderBlend.run();
                            check(!GL30.glIsEnabledi(GL11.GL_BLEND, replaySlot), mode + " replay attachment must not blend");
                            GL20.glUseProgram(program);
                            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Alpha"), alpha);
                            GL20.glUniform1f(GL20.glGetUniformLocation(program, "Tag"), ReplayOutlineContext.currentId());
                            GL30.glBindVertexArray(vao);
                            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                        });
                        tag(0);
                        List<SoftBatch.Entry<SoftDrawEntry>> entries = queue.beginFlush();
                        try
                        {
                            new SoftEntryRunner().run(entries.get(0).spec(), entries.get(0).payload(), false);
                        }
                        finally
                        {
                            queue.endFlush();
                            BlendModeOverride.restore();
                        }
                        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                        FloatBuffer pixel = BufferUtils.createFloatBuffer(4);
                        GL11.glReadPixels(4, 4, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                        check(Math.abs(pixel.get(0) - alpha) < 0.0001F && Math.abs(pixel.get(2) - (1F - alpha)) < 0.0001F,
                            mode + " alpha=" + alpha + " actual red=" + pixel.get(0) + " blue=" + pixel.get(2));
                        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + replaySlot);
                        GL11.glReadPixels(4, 4, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, pixel);
                        check(pixel.get(0) == 17F, mode + " lost queued replay tag: " + pixel.get(0));
                        check(ReplayOutlineContext.currentId() == 0, "Replay scope leaked after draw");
                    }
                }
            }
            finally
            {
                tag(0);
                BlendModeOverride.restore();
                GL20.glDeleteProgram(program);
                GL20.glDeleteShader(vertex);
                GL20.glDeleteShader(fragment);
                GL30.glDeleteVertexArrays(vao);
                GL30.glDeleteFramebuffers(fbo);
                GL11.glDeleteTextures(textures);
            }
        }
        System.out.println("CHECKED: soft and no-shading queue RGB alpha + exact replay attachment: " + mode);
    }

    private static void ownership()
    {
        try (RenderPassState saved = new RenderPassState())
        {
            for (SoftDrawQueue queue : List.of(BBSModClient.getSoftTransparency().getQueue(), BBSModClient.getSoftTransparency().getPostComposite()))
            {
                queue.beginFrame();
                tag(17);
                queue.submit(SoftDrawSpec.forAlpha(0.5F, SoftSortKey.of(0D, 4D)), () ->
                {
                    check(ReplayOutlineContext.currentId() == 17, "Throwing callback lost ownership");
                    throw new IllegalStateException("fixture draw failure");
                });
                tag(99);
                List<SoftBatch.Entry<SoftDrawEntry>> entries = queue.beginFlush();
                try
                {
                    try
                    {
                        new SoftEntryRunner().run(entries.get(0).spec(), entries.get(0).payload(), false);
                        check(false, "Draw failure was swallowed");
                    }
                    catch (IllegalStateException expected)
                    {
                        check(expected.getMessage().equals("fixture draw failure"), "Unexpected callback failure");
                    }
                    check(ReplayOutlineContext.currentId() == 99, "Exception leaked queued ownership");
                }
                finally
                {
                    queue.endFlush();
                }
                queue.beginFrame();
                tag(0);
                queue.submit(SoftDrawSpec.forAlpha(0.5F, SoftSortKey.of(0D, 4D)), () ->
                    check(ReplayOutlineContext.currentId() == 0, "Untagged draw inherited a foreign replay"));
                tag(23);
                entries = queue.beginFlush();
                try
                {
                    new SoftEntryRunner().run(entries.get(0).spec(), entries.get(0).payload(), false);
                    check(ReplayOutlineContext.currentId() == 23, "Untagged draw failed to restore outer ownership");
                }
                finally
                {
                    queue.endFlush();
                }
            }
            tag(0);
            RenderSystem.disableBlend();
            new BufferBlendOverride(1, null).apply();
            check(!GL30.glIsEnabledi(GL11.GL_BLEND, 0), "Fixture must begin with an opaque surface");
            qualet.irlite.client.light.BbsSoftTransparency.applySurfaceBlend();
            check(!GL30.glIsEnabledi(GL11.GL_BLEND, 0), "Compatibility hook changed a non-queued draw");
            BlendModeOverride.restore();
        }
        finally
        {
            tag(0);
        }
        System.out.println("CHECKED: exception restoration, zero ownership and ordinary opaque draws");
    }

    private static void shadows()
    {
        boolean world = BBSRendering.renderingWorld;
        boolean enabled = BBSSettings.irisImprovements.get();
        try
        {
            BBSRendering.renderingWorld = true;
            BBSSettings.irisImprovements.set(true);
            ShadowBakeState.setBaking(true);
            check(!BBSModClient.getSoftTransparency().getGate().canDefer(false, false), "IRLights shadow casters entered the soft queue");
            check(!BBSModClient.getSoftTransparency().getGate().canDeferPostComposite(false, false), "Shadow casters entered the no-shading queue");
        }
        finally
        {
            ShadowBakeState.setBaking(false);
            BBSSettings.irisImprovements.set(enabled);
            BBSRendering.renderingWorld = world;
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
            String pack = Iris.getIrisConfig().getShaderPackName().orElse(null);
            try
            {
                draw.draw();
                setTag = ReplayOutlineContext.class.getDeclaredMethod("set", int.class);
                setTag.setAccessible(true);
                gpu("Iris indexed blend lock", () -> new BufferBlendOverride(1, null).apply(), 1);
                Iris.getIrisConfig().setShaderPackName(System.getProperty("irlite.softCheck.shaderpack"));
                Iris.getIrisConfig().setShadersEnabled(true);
                Iris.getPipelineManager().destroyPipeline();
                Iris.loadShaderpack();
                IrisRenderingPipeline pipeline = (IrisRenderingPipeline) Iris.getPipelineManager().preparePipeline(new NamespacedId("minecraft", "overworld"));
                if (Iris.isFallback()) throw new AssertionError("Shader pipeline unavailable");
                ExtendedShader entity = (ExtendedShader) pipeline.getShaderMap().getShader(ShaderKey.ENTITIES_TRANSLUCENT);
                Method apply = ExtendedShader.class.getDeclaredMethod("applyBlendModes");
                apply.setAccessible(true);
                Field overrides = ExtendedShader.class.getDeclaredField("bufferBlendOverrides");
                overrides.setAccessible(true);
                List<?> buffers = (List<?>) overrides.get(entity);
                if (buffers.isEmpty()) throw new AssertionError("Fixture requires an IRLights-patched shaderpack");
                Field slot = BufferBlendOverride.class.getDeclaredField("drawBuffer");
                slot.setAccessible(true);
                int replaySlot = slot.getInt(buffers.get(buffers.size() - 1));
                gpu("patched Complementary entity shader", () ->
                {
                    try { apply.invoke(entity); }
                    catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                }, replaySlot);
                ownership();
                shadows();
                if (failures != 0) throw new AssertionError(failures + " compatibility failures");
                System.out.println("PASS: soft transparency GPU blend, queued replay ownership and shadow isolation");
            }
            catch (Throwable error)
            {
                System.err.println("FAIL: soft transparency compatibility regression");
                error.printStackTrace();
            }
            finally
            {
                Iris.getIrisConfig().setShaderPackName(pack);
                Iris.getIrisConfig().setShadersEnabled(shaders);
                client.scheduleStop();
            }
        }));
    }
}
