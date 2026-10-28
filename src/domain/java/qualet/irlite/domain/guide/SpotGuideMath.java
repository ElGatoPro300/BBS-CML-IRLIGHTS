package qualet.irlite.domain.guide;

/** Ray/spotlight intersections in guide-local coordinates; NaN means no stable hit. */
public final class SpotGuideMath
{
    private static final float EPSILON = 1E-5F;

    private SpotGuideMath()
    {}

    public static float capZ(float range)
    {
        return Math.max(range, 0.05F);
    }

    public static float range(float ox, float oy, float oz, float dx, float dy, float dz)
    {
        float a = dx * dx + dy * dy + dz * dz;
        float denominator = a - dz * dz;

        if (denominator < EPSILON)
        {
            return Float.NaN;
        }

        float axis = (a * oz - dz * (ox * dx + oy * dy + oz * dz)) / denominator;

        return clamp(Math.signum(capZ(1F)) * axis, 0.1F, 128F);
    }

    public static float angle(float ox, float oy, float oz, float dx, float dy, float dz, float range, float maximum)
    {
        float z = capZ(range);

        if (Math.abs(dz) < EPSILON)
        {
            return Float.NaN;
        }

        float t = (z - oz) / dz;

        if (t <= 0F)
        {
            return Float.NaN;
        }

        float x = ox + dx * t;
        float y = oy + dy * t;
        float radial = (float) Math.sqrt(x * x + y * y);
        float angle = (float) Math.toDegrees(2D * Math.atan2(radial, Math.abs(z)));

        return clamp(angle, 1F, maximum);
    }

    private static float clamp(float value, float minimum, float maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
