package qualet.irlite.client.light;

import qualet.irlite.client.adapter.cml.CmlFilmAccess;
import qualet.irlite.client.light.LightCollector.TraversalScratch;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import io.netty.util.collection.IntObjectMap;

import java.util.List;

/** Scene enumeration adapter; traversal and submission remain shared. */
final class FilmReplayLightSource
{
    private FilmReplayLightSource()
    {}

    static void collect(Vec3d cameraPos, float tickDelta, TraversalScratch scratch)
    {
        FilmEditorController editor = CmlFilmAccess.activeEditor(true);
        if (editor == null || editor.film == null || editor.film.replays == null)
        {
            return;
        }

        List<Replay> replays = editor.film.replays.getList();
        if (replays == null || replays.isEmpty())
        {
            return;
        }

        // BBS 2.6 keys the film's entities by the replay's stable id, not its list index.
        IntObjectMap<IEntity> entities = editor.getEntities();
        for (int replayId = 0; replayId < replays.size(); replayId++)
        {
            Replay replay = replays.get(replayId);
            if (replay == null || replay.actor.get())
            {
                continue;
            }

            IEntity ent = entities.get(replayId);
            if (ent == null)
            {
                continue;
            }

            Form rootForm = ent.getForm();
            if (rootForm == null)
            {
                continue;
            }

            double wx = MathHelper.lerp(tickDelta, ent.getPrevX(), ent.getX());
            double wy = MathHelper.lerp(tickDelta, ent.getPrevY(), ent.getY());
            double wz = MathHelper.lerp(tickDelta, ent.getPrevZ(), ent.getZ());

            double dx = wx - cameraPos.x;
            double dy = wy - cameraPos.y;
            double dz = wz - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > LightCollector.MAX_DIST * LightCollector.MAX_DIST)
            {
                continue;
            }

            // Rotation-only root; the actor's world position rides along as a double base
            // (kept out of the float matrix so it stays precise far from origin). The base
            // is added AFTER the rotation at emit, reproducing the old translate*rotate.
            float bodyYaw = MathHelper.lerp(tickDelta, ent.getPrevBodyYaw(), ent.getBodyYaw());
            int mark = scratch.mark();
            try
            {
                Matrix4f root = scratch.push().local.identity();
                root.rotateY((float) Math.toRadians(-bodyYaw));
                LightFormTraversal.walk(rootForm, root, wx, wy, wz, tickDelta, scratch);
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }
}
