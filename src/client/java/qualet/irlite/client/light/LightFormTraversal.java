package qualet.irlite.client.light;

import qualet.irlite.client.light.LightCollector.ScratchFrame;
import qualet.irlite.client.light.LightCollector.TraversalScratch;
import qualet.irlite.forms.PointLightForm;
import qualet.irlite.forms.SpotlightForm;

import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Transform;

import org.joml.Matrix4f;

import java.util.List;

/** Applies animation states and traverses unbound light parts with balanced cleanup. */
final class LightFormTraversal
{
    private LightFormTraversal()
    {}

    static void walk(Form form, Matrix4f parent, double baseX, double baseY, double baseZ, float transition,
                             TraversalScratch scratch)
    {
        if (form == null)
        {
            return;
        }

        // Overlay this form's animation states before reading its transform, exactly
        // as FormRenderer.render() does (applyStates -> read transforms + walk the
        // subtree -> unapplyStates). BBS lays an animation frame onto a form's Value
        // fields (transform, visible, ...) as a transient runtime override that lives
        // ONLY inside that render window; the scanner runs at renderWorld HEAD, wholly
        // outside it, so without this it would read the static base pose and a
        // ModelBlock-placed light would never follow its form's animation. No-op for
        // a form with no active state players. unapplyStates() sits in finally so the
        // pair stays balanced (including the early returns below), leaving a clean
        // base for the later real render to re-apply from. States are re-applied per
        // form on the recursion, matching the render's per-form apply nesting.
        form.applyStates(transition);
        int mark = scratch.mark();
        try
        {
            if (!form.visible.get())
            {
                return;
            }

            ScratchFrame frame = scratch.push();
            Matrix4f local = frame.local.set(parent);
            Transform t = form.transform.get();
            if (t != null)
            {
                t.setupMatrix(frame.transform.identity());
                local.mul(frame.transform);
            }

            if (form instanceof PointLightForm point)
            {
                LightSubmission.emitPoint(point, local, baseX, baseY, baseZ, frame.origin);
            }
            else if (form instanceof SpotlightForm spot)
            {
                LightSubmission.emitSpot(spot, local, baseX, baseY, baseZ, frame.origin, frame.forward);
            }

            if (form.parts == null)
            {
                return;
            }
            List<BodyPart> parts = form.parts.getAllTyped();
            if (parts == null)
            {
                return;
            }

            for (int i = 0, n = parts.size(); i < n; i++)
            {
                BodyPart part = parts.get(i);
                if (part == null)
                {
                    continue;
                }

                String bone = part.bone.get();
                if (bone != null && !bone.isEmpty())
                {
                    continue;
                }

                Form child = part.getForm();
                if (child == null)
                {
                    continue;
                }

                Matrix4f childM = frame.child.set(local);
                Transform pt = part.transform.get();
                if (pt != null)
                {
                    pt.setupMatrix(frame.transform.identity());
                    childM.mul(frame.transform);
                }

                walk(child, childM, baseX, baseY, baseZ, transition, scratch);
            }
        }
        finally
        {
            try
            {
                form.unapplyStates();
            }
            finally
            {
                scratch.rewind(mark);
            }
        }
    }
}
