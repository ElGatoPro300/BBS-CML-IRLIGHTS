import qualet.irlite.domain.guide.SpotGuideMath;
import qualet.irlite.domain.replay.ReplayFrameState;
import qualet.irlite.domain.replay.ReplaySelections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Behavior contracts executable without Minecraft, CML, Fabric or Iris. */
public final class DomainRegression
{
    private static int assertions;

    private static void check(boolean value, String message)
    {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void near(float actual, float expected)
    {
        check(Float.isNaN(actual) && Float.isNaN(expected) || Math.abs(actual - expected) < 0.0001F,
            "Ray result: " + actual + " != " + expected);
    }

    private static void replayLifecycle()
    {
        ReplayFrameState<Object, Object> state = new ReplayFrameState<>();
        Object world = new Object();
        Object entity = new Object();
        Object form = new Object();
        state.beginFrame(world);
        state.register("film", "a", entity, form);
        int id = state.resolve("film", "a");
        check(id > 0 && state.entity(entity) == id && state.form(form) == id, "Identity ownership");
        check(state.resolve("other", "a") == 0, "Cross-film isolation");
        state.beginFrame(world);
        check(state.resolve("film", "a") == 0 && state.entity(entity) == null, "Frame clears active entries");
        state.register("film", "a", entity, form);
        check(state.resolve("film", "a") == id, "World token remains stable");
        state.set(17);
        Runnable captured = state.capture(() -> check(state.currentId() == 17, "Deferred ownership"));
        state.set(23);
        captured.run();
        check(state.currentId() == 23, "Outer scope restored");
        Runnable throwing = state.capture(() -> { throw new IllegalStateException("fixture"); });
        state.set(99);
        try { throwing.run(); throw new AssertionError("Expected failure"); }
        catch (IllegalStateException expected) { check(state.currentId() == 99, "Exception restoration"); }
        state.set(0);
        Runnable untagged = state.capture(() -> check(state.currentId() == 0, "Zero does not inherit ambient tag"));
        state.set(17);
        untagged.run();
        check(state.currentId() == 17 && state.capture(null) == null, "Null callback and scope");
        ReplayFrameState<Object, Object> other = new ReplayFrameState<>();
        check(other.currentId() == 0 && other.resolve("film", "a") == 0, "Instance isolation");
        state.beginFrame(new Object());
        check(state.resolve("film", "a") == 0 && state.form(form) == null && state.currentId() == 0, "World cleanup");
        state.register("film", "b", entity, form);
        check(state.resolve("film", "b") == 1, "New world restarts tokens");
        state.reset();
        check(state.resolve("film", "b") == 0, "Disconnect cleanup");
    }

    private static void selections()
    {
        LinkedHashSet<String> ids = new LinkedHashSet<>(List.of("b", "a"));
        ReplaySelections.Selection selected = new ReplaySelections.Selection("film", ids, ReplaySelections.Mode.SELECTED);
        ids.clear();
        check(selected.replays().equals(new LinkedHashSet<>(List.of("b", "a"))), "Defensive snapshot");
        check(selected.replays().iterator().next().equals("b"), "Stable order");
        check(selected.belongsTo("film") && !selected.belongsTo(null) && !selected.belongsTo("other"), "Film identifier comparison");
        try { selected.replays().clear(); throw new AssertionError("Mutable selection"); }
        catch (UnsupportedOperationException expected) { check(!selected.isEmpty(), "Immutable selection"); }
    }

    private static void guideMath()
    {
        near(SpotGuideMath.range(1F, 0F, 5F, -1F, 0F, 0F), 5F);
        near(SpotGuideMath.range(0F, 0F, 0F, 0F, 0F, 1F), Float.NaN);
        near(SpotGuideMath.angle(10F, 0F, 0F, 0F, 0F, 1F, 10F, 179F), 90F);
        near(SpotGuideMath.angle(0F, 0F, 0F, 1F, 0F, 0F, 10F, 179F), Float.NaN);
        near(SpotGuideMath.angle(0F, 0F, 11F, 0F, 0F, 1F, 10F, 179F), Float.NaN);
        Random random = new Random(84219L);
        for (int i = 0; i < 10000; i++)
        {
            float ox = random.nextFloat() * 40F - 20F;
            float oy = random.nextFloat() * 40F - 20F;
            float oz = random.nextFloat() * 40F - 20F;
            float dx = random.nextFloat() * 2F - 1F;
            float dy = random.nextFloat() * 2F - 1F;
            float dz = random.nextFloat() * 2F - 1F;
            float range = random.nextFloat() * 128F;
            float maximum = 1F + random.nextFloat() * 178F;
            float a = dx * dx + dy * dy + dz * dz;
            float denominator = a - dz * dz;
            float oldRange = denominator < 1E-5F ? Float.NaN : Math.max(0.1F, Math.min(128F,
                (a * oz - dz * (ox * dx + oy * dy + oz * dz)) / denominator));
            near(SpotGuideMath.range(ox, oy, oz, dx, dy, dz), oldRange);
            float z = Math.max(range, 0.05F);
            float t = (z - oz) / dz;
            float x = ox + dx * t;
            float y = oy + dy * t;
            float oldAngle = Math.abs(dz) < 1E-5F || t <= 0F ? Float.NaN : Math.max(1F, Math.min(maximum,
                (float) Math.toDegrees(2D * Math.atan2(Math.sqrt(x * x + y * y), Math.abs(z)))));
            near(SpotGuideMath.angle(ox, oy, oz, dx, dy, dz, range, maximum), oldAngle);
        }
    }

    public static void main(String[] args)
    {
        replayLifecycle();
        selections();
        guideMath();
        System.out.println("PASS: domain lifecycle, selection and guide math: " + assertions + " assertions, no engine dependencies");
    }
}
