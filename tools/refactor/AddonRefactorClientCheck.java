import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.io.ReplaySelectionCodec;
import qualet.irlite.domain.replay.ReplaySelections;
import qualet.irlite.client.runtime.ClientRuntime;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.Arrays;

/** Differential native regression against the pre-refactor caster math. */
public final class AddonRefactorClientCheck implements ClientModInitializer
{
    private boolean tested;
    private static int checks;

    private static void check(boolean value, String message)
    {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static Method method(Class<?> type, String name, Class<?>... args) throws Exception
    {
        Method method = type.getDeclaredMethod(name, args);
        method.setAccessible(true);
        return method;
    }

    private static Object field(Class<?> type, Object owner, String name) throws Exception
    {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void transform(Transform transform, Random random)
    {
        transform.translate.set(random.nextFloat() * 8F - 4F, random.nextFloat() * 8F - 4F, random.nextFloat() * 8F - 4F);
        transform.scale.set(random.nextFloat() * 6F - 3F, random.nextFloat() * 6F - 3F, random.nextFloat() * 6F - 3F);
        transform.rotate.set(random.nextFloat() * 360F, random.nextFloat() * 360F, random.nextFloat() * 360F);
    }

    private static void bounds() throws Exception
    {
        Class<?> legacy = Class.forName("org.qualet.irl.light.shadow.LegacyBbsCasterSource");
        Class<?> adapted = Class.forName("org.qualet.irl.light.shadow.CasterBounds");
        Constructor<?> constructor = adapted.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object owner = constructor.newInstance();
        Method oldFold = method(legacy, "foldFormChain", Form.class);
        Method newFold = method(adapted, "foldFormChain", Form.class);
        Method oldExpand = method(legacy, "expandInnerBox", Transform.class, float.class, float.class, float.class);
        Method newExpand = method(adapted, "expandInnerBox", Transform.class, float.class, float.class, float.class);
        Random random = new Random(27501L);
        for (int i = 0; i < 1000; i++)
        {
            ModelForm form = new ModelForm();
            transform(form.transform.get(), random);
            transform(form.transformOverlay.get(), random);
            ValueTransform additional = new ValueTransform("fixture", new Transform());
            transform(additional.get(), random);
            form.additionalTransforms.add(additional);
            Transform old = (Transform) oldFold.invoke(null, form);
            Transform current = (Transform) newFold.invoke(owner, form);
            float[] oldScale = ((float[]) field(legacy, null, "foldScale")).clone();
            float[] newScale = ((float[]) field(adapted, owner, "foldScale")).clone();
            check(Arrays.equals(oldScale, newScale), "Folded scale changed");
            float x = 0.1F + random.nextFloat() * 4F;
            float y = 0.1F + random.nextFloat() * 4F;
            float z = 0.1F + random.nextFloat() * 4F;
            oldExpand.invoke(null, old, x * oldScale[0], y * oldScale[1], z * oldScale[2]);
            newExpand.invoke(owner, current, x * newScale[0], y * newScale[1], z * newScale[2]);
            check(Arrays.equals((float[]) field(legacy, null, "innerExt"), (float[]) field(adapted, owner, "innerExt")), "Conservative extents changed");
            check(Arrays.equals((double[]) field(legacy, null, "innerCenter"), (double[]) field(adapted, owner, "innerCenter")), "Mirrored/rotated bounds center changed");
        }
    }

    private static void codec()
    {
        check(ReplaySelectionCodec.encode("film", List.of("b", "a", "b")).equals("{\"film\":\"film\",\"replays\":[\"b\",\"a\"]}"), "Legacy wire format changed");
        for (ReplaySelections.Mode mode : ReplaySelections.Mode.values())
        {
            String value = ReplaySelectionCodec.encode("film", List.of("b", "a"), mode);
            ReplaySelections.Selection decoded = ReplaySelectionCodec.decode(value);
            check(decoded.mode() == mode && decoded.belongsTo("film") && decoded.replays().size() == 2, "Mode round trip");
            PointLightForm form = new PointLightForm();
            form.effects.outlineReplays.set(value);
            PointLightForm copy = new PointLightForm();
            copy.fromData(form.toData());
            check(copy.effects.outlineReplays.get().equals(value), "Saved form compatibility");
        }
        for (String bad : new String[] {null, "", " ", "invalid", "[]", "{\"replays\":[null]}"})
        {
            check(ReplaySelectionCodec.decode(bad).isEmpty(), "Malformed selection must fail closed");
        }
    }

    private static void lifecycle()
    {
        ClientRuntime runtime = ClientRuntime.get();
        Object first = new Object();
        runtime.beginFrame(first);
        runtime.replays().register("film", "a", null, null);
        runtime.replays().set(17);
        long revision = runtime.resources().next();
        runtime.beginFrame(new Object());
        check(runtime.replays().currentId() == 0 && runtime.replays().resolve("film", "a") == 0, "World ownership reset");
        check(runtime.resources().next() > revision, "Resource revisions must survive world changes");
        check(!runtime.spotGuide().isDragging(), "World change stops dragging");
        runtime.profiler().registerPassName(new Object(), "fixture");
        check(!runtime.profiler().isEnabled(), "Profiler starts disabled in this fixture");
        runtime.profiler().toggle();
        runtime.profiler().frameTick();
        check(runtime.profiler().isEnabled(), "Profiler toggle enabled");
        runtime.profiler().beginPass("fixture");
        runtime.profiler().endPass();
        runtime.profiler().frameTick();
        runtime.profiler().toggle();
        runtime.profiler().frameTick();
        check(!runtime.profiler().isEnabled(), "Profiler toggle disabled and query cleanup");
        runtime.reset();
        check(runtime.replays().currentId() == 0, "Disconnect reset");
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
                bounds();
                codec();
                lifecycle();
                System.out.println("PASS: addon refactor native contracts: " + checks + " checks");
            }
            catch (Throwable error)
            {
                System.err.println("FAIL: addon refactor native contracts");
                error.printStackTrace();
            }
            finally
            {
                client.scheduleStop();
            }
        }));
    }
}
