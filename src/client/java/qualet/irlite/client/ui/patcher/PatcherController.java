package qualet.irlite.client.ui.patcher;

import qualet.irlite.client.patcher.PatchOperations;
import qualet.irlite.client.ui.IRLightsUIKeys;

import org.qualet.irl.patcher.IrlPatch;
import org.qualet.irl.patcher.PatchLibrary;
import org.qualet.irl.patcher.PatchResult;
import org.qualet.irl.patcher.Shaderpacks;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The IRLite shader patcher, rendered as controls inside the IRLite settings section. */
public final class PatcherController
{
    private static final Logger LOG = LoggerFactory.getLogger("irlite");

    private static final int OK_COLOR = 0x55FF55;
    private static final int ERR_COLOR = 0xFF5555;
    private static final int META_COLOR = 0xAAAAAA;
    private static final int WARN_COLOR = 0xFFAA33;

    private final PatchOperations operations = new PatchOperations();

    // Selection persists across settings refreshes.
    private String selectedPack;
    private Path selectedPatch;
    private boolean createNew = false;
    private IKey status;
    private int statusColor = Colors.WHITE;

    // UIText (not UILabel): both wrap onto multiple lines instead of truncating with
    // "..." at the panel edge. A failed-patch status is a full sentence, and the meta
    // line carries the target shaderpack name — cutting either off soft-locks the user
    // (they can't see what went wrong or which pack the patch actually needs).
    private UIText metaLabel;
    private UIText statusLabel;
    private Runnable rebuild;

    public PatcherController()
    {}

    public void append(UIScrollView options, Runnable rebuildCallback)
    {
        /* CML initializes localization after the Fabric client entrypoint. */
        if (this.status == null)
        {
            this.status = IRLightsUIKeys.SELECT_BOTH;
        }

        rebuild = rebuildCallback;

        List<String> packs = Shaderpacks.list();
        List<Path> patches = PatchLibrary.list();

        // --- shaderpack list (header row: label + refresh + open folder) ---
        UIIcon refresh = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (this.rebuild != null)
            {
                rebuild.run();
            }
        });
        refresh.tooltip(IRLightsUIKeys.REFRESH);

        UIIcon openPacks = new UIIcon(Icons.FOLDER, (b) -> Shaderpacks.openFolder());
        openPacks.tooltip(IRLightsUIKeys.OPEN_PACKS);

        options.add(headerRow(IRLightsUIKeys.PACKS, refresh, openPacks));

        UILabelList<String> packList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPack = selected.get(0).value;
                updateMeta(null);
            }
        });
        packList.background();
        packList.h(80);
        for (String pack : packs)
        {
            packList.add(IKey.constant(pack), pack);
        }
        if (this.selectedPack != null)
        {
            packList.setCurrentValue(this.selectedPack);
        }
        options.add(packList);

        // --- patch list (header row: label + open folder) ---
        UIIcon openPatches = new UIIcon(Icons.FOLDER, (b) -> PatchLibrary.openFolder());
        openPatches.tooltip(IRLightsUIKeys.OPEN_PATCHES);

        options.add(headerRow(IRLightsUIKeys.PATCHES, openPatches));

        UILabelList<Path> patchList = new UILabelList<>((selected) ->
        {
            if (!selected.isEmpty())
            {
                selectedPatch = selected.get(0).value;
                updateMeta(packList);
            }
        });
        patchList.background();
        patchList.h(80);
        for (Path patch : patches)
        {
            patchList.add(IKey.constant(patch.getFileName().toString()), patch);
        }
        if (this.selectedPatch != null)
        {
            patchList.setCurrentValue(this.selectedPatch);
        }
        options.add(patchList);

        // --- selected-patch meta (which shaderpack it's for + match state) ---
        metaLabel = new UIText(IKey.EMPTY).color(META_COLOR, true);
        options.add(this.metaLabel);
        updateMeta(packList);

        // --- options + primary actions ---
        UIToggle createNewToggle = new UIToggle(IRLightsUIKeys.CREATE_NEW, (t) -> this.createNew = t.getValue());
        createNewToggle.setValue(this.createNew);
        options.add(createNewToggle);

        UIButton validate = new UIButton(IRLightsUIKeys.VALIDATE, (b) -> runValidate());
        validate.tooltip(IRLightsUIKeys.VALIDATE_HELP);
        UIButton patch = new UIButton(IRLightsUIKeys.PATCH, (b) -> runPatch());
        options.add(UI.row(validate, patch));

        statusLabel = new UIText(this.status).color(this.statusColor, true);
        options.add(this.statusLabel);
    }

    /** Parses the selected patch for the metadata line; auto-selects a matching pack when none is chosen. */
    private void updateMeta(UILabelList<String> packList)
    {
        if (this.metaLabel == null)
        {
            return;
        }
        if (this.selectedPatch == null)
        {
            setMeta(IKey.EMPTY, META_COLOR);
            return;
        }

        IrlPatch parsed;
        try
        {
            parsed = this.operations.read(this.selectedPatch);
        }
        catch (Exception e)
        {
            LOG.warn("failed to parse patch {}", this.selectedPatch, e);
            setMeta(IRLightsUIKeys.READ_FAILED, ERR_COLOR);
            return;
        }

        if (packList != null && this.selectedPack == null && !parsed.target.isEmpty())
        {
            List<String> matching = new ArrayList<>();
            for (String pack : Shaderpacks.list())
            {
                if (Shaderpacks.packMatchesTarget(pack, parsed.target))
                {
                    matching.add(pack);
                }
            }
            if (matching.size() == 1)
            {
                selectedPack = matching.get(0);
                packList.setCurrentValue(this.selectedPack);
            }
        }

        // Friendly, plain-language meta line: which shaderpack the patch is for and
        // whether the selected pack matches. Colour carries the signal (green = matches,
        // amber = different pack, red = unreadable) — no jargon, no op counts.
        boolean hasTarget = !parsed.target.isEmpty();

        if (this.selectedPack == null)
        {
            // A patch is chosen but no shaderpack yet — point the user at the right one.
            setMeta(hasTarget
                ? IRLightsUIKeys.TARGET_CHOOSE.format(parsed.target)
                : IRLightsUIKeys.CHOOSE_PACK, META_COLOR);
        }
        else if (hasTarget && !Shaderpacks.packMatchesTarget(this.selectedPack, parsed.target))
        {
            setMeta(IRLightsUIKeys.TARGET_MISMATCH.format(parsed.target), WARN_COLOR);
        }
        else
        {
            setMeta(IRLightsUIKeys.TARGET_MATCH.format(hasTarget ? parsed.target : this.selectedPack), OK_COLOR);
        }
    }

    /** Shared head of Validate/Patch: both need a pack, a patch and a successful parse. */
    private IrlPatch parseSelected()
    {
        if (this.selectedPack == null)
        {
            setStatus(false, IRLightsUIKeys.CHOOSE_PACK);
            return null;
        }
        if (this.selectedPatch == null)
        {
            setStatus(false, IRLightsUIKeys.CHOOSE_PATCH);
            return null;
        }

        try
        {
            return this.operations.read(this.selectedPatch);
        }
        catch (Exception e)
        {
            LOG.warn("failed to parse patch {}", this.selectedPatch, e);
            setStatus(false, IRLightsUIKeys.READ_FAILED);
            return null;
        }
    }

    private void runValidate()
    {
        IrlPatch parsed = parseSelected();
        if (parsed == null)
        {
            return;
        }

        PatchResult result = this.operations.validate(this.selectedPack, parsed);
        for (String line : result.log)
        {
            LOG.info("[validate] {}", line);
        }
        applyResult(true, result, null);
    }

    private void runPatch()
    {
        IrlPatch parsed = parseSelected();
        if (parsed == null)
        {
            return;
        }

        String outName = Shaderpacks.outputName(parsed, this.selectedPack, this.createNew);
        PatchResult result = this.operations.apply(this.selectedPack, outName, parsed);

        for (String line : result.log)
        {
            LOG.info("[patch] {}", line);
        }
        applyResult(false, result, outName);

        // Re-read lists so a newly created patched pack shows up.
        if (this.rebuild != null)
        {
            rebuild.run();
        }
    }

    /** Maps the shared irl-core {@link PatchResult} into one friendly status line.
     *  The full per-op detail still goes to the {@code irlite} log; this keeps the
     *  shared engine text untouched (the user never sees the raw English summary). */
    private void applyResult(boolean validate, PatchResult result, String outputName)
    {
        if (result.ok)
        {
            setStatus(true, validate
                ? IRLightsUIKeys.VALID
                : IRLightsUIKeys.CREATED.format(outputName));
            return;
        }

        IKey message;
        switch (result.outcome)
        {
            case ALREADY_PATCHED:
            case ADD_FILE_EXISTS:
                message = IRLightsUIKeys.ALREADY_PATCHED;
                break;
            case CONTRACT_MISMATCH:
                message = IRLightsUIKeys.CONTRACT_MISMATCH;
                break;
            case BAD_SOURCE:
                message = IRLightsUIKeys.BAD_SOURCE;
                break;
            case IO_ERROR:
                message = IRLightsUIKeys.IO_ERROR;
                break;
            default:
                message = IRLightsUIKeys.INCOMPATIBLE;
                break;
        }
        setStatus(false, message);
    }

    private void setMeta(IKey message, int color)
    {
        if (this.metaLabel != null)
        {
            metaLabel.text(message);
            metaLabel.color(color, true);
        }
    }

    private void setStatus(boolean ok, IKey message)
    {
        status = message;
        statusColor = ok ? OK_COLOR : ERR_COLOR;
        if (this.statusLabel != null)
        {
            statusLabel.text(message);
            statusLabel.color(this.statusColor, true);
        }
    }

    /** A header row with the label flexing on the left and fixed icon buttons on the right (icons vertically centered with the text). */
    private UIElement headerRow(IKey text, UIIcon... icons)
    {
        int rowH = 18;

        UIElement row = new UIElement();
        row.row(4).preferred(0);
        row.h(rowH);
        row.marginTop(6);

        // Vertically center the label text so it lines up with the centered icons.
        // anchorY centers within (area.h - fontHeight), so the label must span the
        // full row height — UILabel otherwise auto-sizes to the font height.
        UILabel header = UI.label(text);
        header.labelAnchor(0F, 0.5F);
        header.h(rowH);
        row.add(header);
        for (UIIcon icon : icons)
        {
            icon.w(20).h(rowH);
            row.add(icon);
        }

        return row;
    }
}
