package qualet.irlite.client.light;

import qualet.irlite.client.light.LightCollector.ScratchFrame;
import qualet.irlite.client.light.LightCollector.TraversalScratch;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import java.util.List;

/** Scene enumeration adapter; traversal and submission remain shared. */
final class ModelBlockLightSource
{
    private ModelBlockLightSource()
    {}

    static void collect(ClientWorld world, Vec3d cameraPos, float tickDelta, TraversalScratch scratch)
    {
        List<ModelBlockEntity> blocks = ModelBlockScan.get(world, cameraPos);

        for (int i = 0, n = blocks.size(); i < n; i++)
        {
            ModelBlockEntity mbe = blocks.get(i);
            BlockPos pos = mbe.getPos();

            ModelProperties props;
            try { props = mbe.getProperties(); }
            catch (Throwable t) { continue; }
            if (props == null || !props.isEnabled())
            {
                continue;
            }

            Form rootForm = props.getForm();
            if (rootForm == null)
            {
                continue;
            }

            // Keep the (potentially huge) world coordinate OUT of the float matrix:
            // build the form tree RELATIVE to the block cell and carry the block's
            // world position as a double base, added back only at emit. At X=100000 a
            // float matrix would quantize the position to ~8 mm; a double base does not.
            int mark = scratch.mark();
            try
            {
                ScratchFrame frame = scratch.push();
                Matrix4f root = frame.local.identity();
                root.translate(0.5F, 0F, 0.5F);
                Transform propsT = props.getTransform();
                if (propsT != null)
                {
                    propsT.setupMatrix(frame.transform.identity());
                    root.mul(frame.transform);
                }

                LightFormTraversal.walk(rootForm, root, pos.getX(), pos.getY(), pos.getZ(), tickDelta, scratch);
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }
}
