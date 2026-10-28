package org.qualet.irl.light.shadow;

import qualet.irlite.client.light.BbsMobSilhouette;
import qualet.irlite.client.light.BbsModelSilhouette;
import qualet.irlite.client.light.BbsSilhouetteBridge;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.forms.MobForm;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

/** Thin CML adapter for the Core shadow source contract. */
public final class IRLiteBbsCasterSource implements ShadowCasterSource
{
    private final BbsModelSilhouette silhouettes = new BbsModelSilhouette();
    private final BbsMobSilhouette mobSilhouettes = new BbsMobSilhouette();
    private final CasterCollector collector = new CasterCollector();
    private final CasterGeometry geometry = new CasterGeometry();

    @Override
    public CasterRevision revision(Object caster, int type, float tickDelta)
    {
        if (type != CasterType.MODEL_BLOCK || !(caster instanceof ModelBlockEntity block)) return CasterRevision.UNKNOWN;
        try
        {
            return block.getProperties() != null && block.getProperties().getForm() instanceof MobForm
                ? mobSilhouettes.sample(block, tickDelta) : silhouettes.sample(block, tickDelta);
        }
        catch (RuntimeException | LinkageError failure)
        {
            // INVARIANT 4 scoping: a probe failure (BBS drift past the audited layouts)
            // degrades to UNKNOWN for that caster. Reported once so it never hides as
            // "no reuse"; the self-test's AssertionError deliberately propagates.
            BbsSilhouetteBridge.reportFailure(failure);
            return CasterRevision.UNKNOWN;
        }
    }

    @Override
    public void collect(ClientWorld world, Vec3d camera, float tickDelta, OccluderSink sink)
    {
        this.silhouettes.beginFrame();
        this.collector.collect(world, camera, tickDelta, sink);
    }

    @Override
    public void emitOccluder(Object caster, int type, float tickDelta, OccluderBatch batch)
    {
        this.geometry.emit(caster, type, tickDelta, batch);
    }
}
