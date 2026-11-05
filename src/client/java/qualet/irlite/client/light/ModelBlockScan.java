package qualet.irlite.client.light;

import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model blocks within {@link LightCollector#MAX_DIST} of the camera, in ticker order.
 * The light scan and the shadow caster scan both need this list every frame; walking every
 * block entity ticker of the world (chests, furnaces, hoppers...) once per frame instead of
 * twice halves that cost. The cache is keyed by frame, world and camera, so a caller with a
 * different camera still gets an exact list.
 */
public final class ModelBlockScan
{
    private static final List<ModelBlockEntity> BLOCKS = new ArrayList<>();
    private static long frame;
    private static long scannedFrame = -1;
    private static ClientWorld scannedWorld;
    private static double scannedX, scannedY, scannedZ;

    private ModelBlockScan()
    {}

    /** Once per frame, before any collector runs. */
    public static void beginFrame()
    {
        frame++;
    }

    public static List<ModelBlockEntity> get(ClientWorld world, Vec3d camera)
    {
        return get(world, camera.x, camera.y, camera.z);
    }

    public static List<ModelBlockEntity> get(ClientWorld world, double camX, double camY, double camZ)
    {
        if (scannedFrame == frame && scannedWorld == world && scannedX == camX && scannedY == camY && scannedZ == camZ)
        {
            return BLOCKS;
        }

        BLOCKS.clear();
        scannedFrame = frame;
        scannedWorld = world;
        scannedX = camX;
        scannedY = camY;
        scannedZ = camZ;

        List<BlockEntityTickInvoker> tickers;
        try
        {
            tickers = ((WorldBlockEntityTickersAccessor) (Object) world).irlite$getBlockEntityTickers();
        }
        catch (Throwable t)
        {
            return Collections.emptyList();
        }
        if (tickers == null)
        {
            return Collections.emptyList();
        }

        double maxSq = LightCollector.MAX_DIST * LightCollector.MAX_DIST;
        for (int i = 0, n = tickers.size(); i < n; i++)
        {
            BlockEntityTickInvoker invoker = tickers.get(i);
            if (invoker == null)
            {
                continue;
            }

            BlockPos pos = invoker.getPos();
            if (pos == null)
            {
                continue;
            }

            double dx = pos.getX() + 0.5 - camX;
            double dy = pos.getY() + 0.5 - camY;
            double dz = pos.getZ() + 0.5 - camZ;
            if (dx * dx + dy * dy + dz * dz > maxSq)
            {
                continue;
            }

            BlockEntity be;
            try { be = world.getBlockEntity(pos); }
            catch (Throwable t) { continue; }
            if (be instanceof ModelBlockEntity mbe)
            {
                BLOCKS.add(mbe);
            }
        }

        return BLOCKS;
    }
}
