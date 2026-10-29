package qualet.irlite.client.diag;

import qualet.irlite.IrliteConfig;
import qualet.irlite.platform.RenderApi;

import org.qualet.irl.light.shadow.ShadowBaker;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import com.google.gson.GsonBuilder;

import org.lwjgl.opengl.GL11;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Optional raw capture; I/O failure disables export, never the renderer. */
public final class ProfileCapture
{
    private static final boolean CSV = Boolean.getBoolean("irlite.profileCsv");
    private BufferedWriter writer;

    ProfileCapture() {}

    void start(boolean detailed, boolean sweep)
    {
        Map<String, Object> metadata = new LinkedHashMap<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        metadata.put("schema", 1);
        boolean irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
        metadata.put("shaderpack", irisLoaded ? Iris.getCurrentPackName() : "Iris not loaded");
        metadata.put("irisFallback", irisLoaded && Iris.isFallback());
        metadata.put("coreVersion", version("irl-core"));
        metadata.put("addonVersion", version("irlite"));
        metadata.put("coreClassSha256", classHash(ShadowBaker.class));
        metadata.put("profilerClassSha256", classHash(ProfileSession.class));
        metadata.put("coreClassResource", String.valueOf(ShadowBaker.class.getResource("ShadowBaker.class")));
        metadata.put("java", System.getProperty("java.version"));
        metadata.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
        metadata.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
        metadata.put("width", mc.getWindow().getFramebufferWidth());
        metadata.put("height", mc.getWindow().getFramebufferHeight());
        metadata.put("shadowQuality", IrliteConfig.shadowQuality());
        metadata.put("partialTile", IrliteConfig.shadowPartialTile());
        metadata.put("overlayReuseDisabled", Boolean.getBoolean("irlite.noOverlayReuse"));
        metadata.put("casterRevisionSchema", 1);
        metadata.put("surfaceShadows", IrliteConfig.shadowsLive());
        metadata.put("shadowSoftness", IrliteConfig.shadowSoftness());
        metadata.put("vlShadows", IrliteConfig.vlShadowsLive());
        metadata.put("vlIntensity", IrliteConfig.vlIntensity());
        metadata.put("vlSteps", IrliteConfig.vlSteps());
        metadata.put("vlShadowStride", IrliteConfig.vlShadowStride());
        metadata.put("vlNoise", IrliteConfig.vlNoiseLive());
        metadata.put("vlNoiseStride", IrliteConfig.vlNoiseStride());
        metadata.put("vlClusterCull", IrliteConfig.vlClusterCull());
        metadata.put("detailedShadowTimers", detailed);
        metadata.put("vlSweep", sweep);
        metadata.put("world", mc.world == null ? null : mc.world.getRegistryKey().getValue().toString());
        metadata.put("camera", RenderApi.cameraPosition(RenderApi.camera()).toString());
        metadata.put("cameraYaw", RenderApi.camera().getYaw());
        metadata.put("cameraPitch", RenderApi.camera().getPitch());
        String json = new GsonBuilder().serializeNulls().create().toJson(metadata);
        System.out.println("[irlite] profile-session: " + json);
        if (!CSV) return;
        try
        {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("logs/irlite-perf");
            Files.createDirectories(dir);
            Path csv = Files.createTempFile(dir, "capture-", ".csv");
            Files.writeString(csv.resolveSibling(csv.getFileName() + ".json"), json, StandardCharsets.UTF_8);
            writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8);
            writer.write("kind,frame,name,value\n");
            System.out.println("[irlite] profile-csv: " + csv.toAbsolutePath());
        }
        catch (IOException e)
        {
            failed(e);
        }
    }

    /** Hash Iris's assembled source inputs at program creation, before its GL transforms. */
    public void programSource(ProgramSource source)
    {
        if (!VlProfiler.isEnabled()) return;
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[] {source.getName(), source.getVertexSource().orElse(""),
                source.getGeometrySource().orElse(""), source.getTessControlSource().orElse(""),
                source.getTessEvalSource().orElse(""), source.getFragmentSource().orElse("")})
            {
                digest.update(part.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            System.out.println("[irlite] profile-source: " + source.getName()
                + " sha256=" + HexFormat.of().formatHex(digest.digest()));
        }
        catch (Exception e)
        {
            System.out.println("[irlite] profile-source: unavailable: " + e);
        }
    }

    private String version(String id)
    {
        return FabricLoader.getInstance().getModContainer(id)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("missing");
    }

    private String classHash(Class<?> type)
    {
        try (InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class"))
        {
            if (stream == null) return "unavailable";
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception e)
        {
            return "unavailable: " + e.getClass().getSimpleName();
        }
    }

    void sample(String kind, long frame, String name, long value)
    {
        if (this.writer == null) return;
        try
        {
            writer.write(kind + "," + frame + ",\"" + name.replace("\"", "\"\"") + "\"," + value + "\n");
        }
        catch (IOException e)
        {
            failed(e);
        }
    }

    void flush()
    {
        if (this.writer == null) return;
        try { this.writer.flush(); }
        catch (IOException e) { failed(e); }
    }

    void close()
    {
        if (this.writer == null) return;
        BufferedWriter closing = this.writer;
        writer = null;
        try { closing.close(); }
        catch (IOException e) { System.out.println("[irlite] profile-csv: close failed: " + e); }
    }

    private void failed(IOException e)
    {
        close();
        System.out.println("[irlite] profile-csv: export disabled: " + e);
    }
}
