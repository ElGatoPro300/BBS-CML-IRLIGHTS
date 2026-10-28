package org.qualet.irl.light.shadow;

import qualet.irlite.client.adapter.cml.CmlFilmAccess;
import qualet.irlite.client.light.BbsModelSilhouette;
import qualet.irlite.client.light.LightCollector;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;
import qualet.irlite.mixin.client.bbs.FilmsAccessor;
import qualet.irlite.mixin.client.bbs.WorldBlockEntityTickersAccessor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.BlockEntityTickInvoker;

import io.netty.util.collection.IntObjectMap;

import java.util.List;

/** Enumerates entity, model-block and replay candidates in the original stable order. */
final class CasterCollector
{
    private static final double COLLECT_DIST_SQ = LightCollector.MAX_DIST * LightCollector.MAX_DIST;
    private final CasterBounds bounds = new CasterBounds();
    private int lastModelBlocks = -1;

    public void collect(ClientWorld world, Vec3d camPos, float tickDelta, OccluderSink sink)
    {
        double camX = camPos.x, camY = camPos.y, camZ = camPos.z;

        // --- Arm 1: world entities (vanilla / BBS-morph render path) ---
        for (Entity entity : world.getEntities())
        {
            if (!(entity instanceof LivingEntity) && !(entity instanceof ItemEntity))
            {
                continue;
            }

            double ex = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
            double ey = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
            double ez = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());
            double dx = ex - camX, dy = ey - camY, dz = ez - camZ;
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
            {
                continue;
            }

            // emitFromBox raises the center to mid-height and derives the
            // circumscribing box-diagonal radius (INVARIANT 5); the sink retains
            // the bounded nearest set. Entities are always dynamic -> isStatic
            // false, staticHash 0 (INVARIANT 2). A morphed player/actor DRAWS
            // its BBS form transformed by the form chain (feet + R_yaw·(T_f +
            // R_f·S_f·geom)) while the vanilla box does not follow it — a
            // non-identity chain gets the replay-arm treatment on the vanilla
            // box dims: fold + primary-rotation expansion, yaw-symmetrized
            // horizontals, signed vertical center. Identity chains (all plain
            // entities) keep the legacy emitFromBox channel (tight rh/hv).
            Form morphForm = entityMorphForm(entity);
            Transform morphFt = morphForm == null ? null : this.bounds.foldFormChain(morphForm);
            if (morphForm == null || this.bounds.foldIsIdentity(morphFt))
            {
                sink.emitFromBox(entity, CasterType.ENTITY, false, ex, ey, ez, entity.getBoundingBox(), 1F, 0L);
            }
            else
            {
                Box box = entity.getBoundingBox();
                float bhx = (float) (box.maxX - box.minX) * 0.5F;
                float bhy = (float) (box.maxY - box.minY) * 0.5F;
                float bhz = (float) (box.maxZ - box.minZ) * 0.5F;
                this.bounds.expandInnerBox(morphFt, bhx * this.bounds.foldScale[0], bhy * this.bounds.foldScale[1], bhz * this.bounds.foldScale[2]);
                float hh = (float) (Math.sqrt((double) this.bounds.innerExt[0] * this.bounds.innerExt[0] + (double) this.bounds.innerExt[2] * this.bounds.innerExt[2])
                    + Math.sqrt(this.bounds.innerCenter[0] * this.bounds.innerCenter[0] + this.bounds.innerCenter[2] * this.bounds.innerCenter[2]));
                float rad = (float) Math.sqrt((double) hh * hh + (double) this.bounds.innerExt[1] * this.bounds.innerExt[1]) + CasterBounds.OVERLAP_MARGIN;
                sink.emit(entity, CasterType.ENTITY, false,
                    (float) ex, (float) (ey + this.bounds.innerCenter[1]), (float) ez, rad, 0L);
            }
        }

        // --- Arm 2: BBS model blocks (BlockEntity, not in world.getEntities()) ---
        collectModelBlocks(world, camX, camY, camZ, sink);

        // --- Arm 3: BBS film replays (non-actor stubs; actors come via Arm 1) ---
        collectFilmReplays(camX, camY, camZ, tickDelta, sink);
    }

    private void collectModelBlocks(ClientWorld world, double camX, double camY, double camZ, OccluderSink sink)
    {
        // INVARIANT 4 scoping: all BBS reflection/accessor try/catch stays INSIDE
        // this collect arm. A throwing collect for one caster degrades to
        // "absent", never aborts the bake.
        List<BlockEntityTickInvoker> tickers;
        try
        {
            tickers = ((WorldBlockEntityTickersAccessor) (Object) world).irlite$getBlockEntityTickers();
        }
        catch (Throwable t)
        {
            return;
        }
        if (tickers == null)
        {
            return;
        }

        int collected = 0;
        for (int idx = 0, n = tickers.size(); idx < n; idx++)
        {
            BlockEntityTickInvoker invoker = tickers.get(idx);
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
            if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
            {
                continue;
            }

            BlockEntity be;
            try { be = world.getBlockEntity(pos); }
            catch (Throwable t) { continue; }
            if (!(be instanceof ModelBlockEntity mbe))
            {
                continue;
            }

            ModelProperties props;
            try { props = mbe.getProperties(); }
            catch (Throwable t) { continue; }
            if (props == null || !props.isEnabled())
            {
                continue;
            }
            Form form = props.getForm();
            if (!hasShadowGeometry(form))
            {
                continue;
            }
            Transform t = props.getTransform();
            this.bounds.emitModelBlock(sink, mbe, form, t);
            collected++;
        }
        if (BbsModelSilhouette.DEBUG && collected != this.lastModelBlocks)
        {
            // -Dirlite.debugCasterRevisions: a caster that stops being collected
            // is invisible to the per-caster UNKNOWN trace, so report set changes.
            System.out.println("[irlite] caster-collect: model blocks " + collected + " (was " + this.lastModelBlocks + ")");
            lastModelBlocks = collected;
        }
    }

    private void collectFilmReplays(double camX, double camY, double camZ, float tickDelta, OccluderSink sink)
    {
        // INVARIANT 4 scoping: all BBS reflection/accessor try/catch stays INSIDE.
        Films films;
        try { films = BBSModClient.getFilms(); }
        catch (Throwable t) { return; }
        if (films == null)
        {
            return;
        }

        List<BaseFilmController> ctrls;
        try { ctrls = ((FilmsAccessor) (Object) films).irlite$getControllers(); }
        catch (Throwable t) { ctrls = null; }

        FilmEditorController editor = CmlFilmAccess.activeEditor(false);
        int worldN = ctrls == null ? 0 : ctrls.size();
        int total = worldN + (editor != null ? 1 : 0);

        for (int ci = 0; ci < total; ci++)
        {
            BaseFilmController ctrl = ci < worldN ? ctrls.get(ci) : editor;
            if (ctrl == null || ctrl.film == null || ctrl.film.replays == null)
            {
                continue;
            }

            List<Replay> replays;
            try { replays = ctrl.film.replays.getList(); }
            catch (Throwable t) { continue; }
            if (replays == null || replays.isEmpty())
            {
                continue;
            }

            // BBS 2.6 keys the film's entities by the replay's stable id, not its list index.
            IntObjectMap<IEntity> entities = ctrl.getEntities();
            for (int rid = 0; rid < replays.size(); rid++)
            {
                Replay replay = replays.get(rid);
                if (replay == null || replay.actor.get())
                {
                    // Skip actor replays — real actors come via the entity arm.
                    continue;
                }
                IEntity ent = entities.get(rid);
                if (ent == null)
                {
                    continue;
                }
                Form form = ent.getForm();
                if (!hasShadowGeometry(form))
                {
                    continue;
                }

                double wx = MathHelper.lerp(tickDelta, ent.getPrevX(), ent.getX());
                double wy = MathHelper.lerp(tickDelta, ent.getPrevY(), ent.getY());
                double wz = MathHelper.lerp(tickDelta, ent.getPrevZ(), ent.getZ());

                double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
                if (dx * dx + dy * dy + dz * dz > COLLECT_DIST_SQ)
                {
                    continue;
                }

                // INVARIANT 5: replay form hitbox, grown by the form chain the
                // DRAW applies (form.transform + overlays + config scale — the
                // old raw-hitbox box under-bounded any scaled replay form), then
                // rotation-expanded by the primary form rotation. Body yaw is a
                // per-frame rotation about the feet axis, so the horizontal
                // extent is SYMMETRIZED (box corner radius + horizontal center
                // offset) to stay yaw-invariant; the vertical center keeps the
                // form-translate offset. Non-identity chains use the raw emit
                // because that sphere is neither feet-centered nor expressible
                // as emitFromBox's raised box (rh=hv=radius — conservative for
                // the spot dyn-rect); identity chains keep emitFromBox. Replays
                // are always dynamic -> isStatic false, staticHash 0 (INVARIANT 2).
                float hbW = Math.max(0.1F, form.hitboxWidth.get());
                float hbH = Math.max(0.1F, form.hitboxHeight.get());
                Transform ft = this.bounds.foldFormChain(form);
                if (this.bounds.foldIsIdentity(ft))
                {
                    // Identity chain (the common untransformed replay): keep the
                    // legacy emitFromBox channel — its tight per-axis rh/hv keep
                    // the spot dyn-rect small; the raw-sphere fallback would
                    // quadruple the rect area for every ordinary replay.
                    Box box = new Box(-hbW * 0.5, 0, -hbW * 0.5, hbW * 0.5, hbH, hbW * 0.5);
                    sink.emitFromBox(ent, CasterType.REPLAY, false, wx, wy, wz, box, 1F, 0L);
                }
                else
                {
                    this.bounds.expandInnerBox(ft, hbW * 0.5F * this.bounds.foldScale[0], hbH * 0.5F * this.bounds.foldScale[1], hbW * 0.5F * this.bounds.foldScale[2]);
                    float hh = (float) (Math.sqrt((double) this.bounds.innerExt[0] * this.bounds.innerExt[0] + (double) this.bounds.innerExt[2] * this.bounds.innerExt[2])
                        + Math.sqrt(this.bounds.innerCenter[0] * this.bounds.innerCenter[0] + this.bounds.innerCenter[2] * this.bounds.innerCenter[2]));
                    float rad = (float) Math.sqrt((double) hh * hh + (double) this.bounds.innerExt[1] * this.bounds.innerExt[1]) + CasterBounds.OVERLAP_MARGIN;
                    sink.emit(ent, CasterType.REPLAY, false,
                        (float) wx, (float) (wy + this.bounds.innerCenter[1]), (float) wz, rad, 0L);
                }
            }
        }
    }

    private boolean hasShadowGeometry(Form form)
    {
        if (form == null)
        {
            return false;
        }
        if (!(form instanceof PointLightForm) && !(form instanceof SpotlightForm))
        {
            return true;
        }

        List<BodyPart> parts = form.parts.getAllTyped();
        if (parts == null)
        {
            return false;
        }
        for (int i = 0, n = parts.size(); i < n; i++)
        {
            BodyPart part = parts.get(i);
            if (part != null && hasShadowGeometry(part.getForm()))
            {
                return true;
            }
        }
        return false;
    }

    private Form entityMorphForm(Entity entity)
    {
        try
        {
            if (entity instanceof AbstractClientPlayerEntity player)
            {
                Morph morph = Morph.getMorph(player);
                return morph == null ? null : morph.getForm();
            }
            if (entity instanceof ISelectorOwnerProvider provider)
            {
                SelectorOwner owner = provider.getOwner();
                return owner == null ? null : owner.getForm();
            }
            return null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
