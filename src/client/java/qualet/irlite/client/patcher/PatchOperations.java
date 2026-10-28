package qualet.irlite.client.patcher;

import org.qualet.irl.patcher.IrlPatch;
import org.qualet.irl.patcher.IrlPatchApplier;
import org.qualet.irl.patcher.IrlPatchParser;
import org.qualet.irl.patcher.PatchResult;
import org.qualet.irl.patcher.Shaderpacks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Non-UI patch operations; Core retains parsing and patch application semantics. */
public final class PatchOperations
{
    public IrlPatch read(Path patch) throws IOException, IrlPatchParser.ParseException
    {
        return IrlPatchParser.parse(Files.readString(patch, StandardCharsets.UTF_8));
    }

    public PatchResult validate(String pack, IrlPatch patch)
    {
        return IrlPatchApplier.validate(Shaderpacks.packPath(pack), patch);
    }

    public PatchResult apply(String pack, String output, IrlPatch patch)
    {
        return IrlPatchApplier.apply(Shaderpacks.packPath(pack), Shaderpacks.dir().resolve(output), patch);
    }
}
