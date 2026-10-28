package qualet.irlite.io;

import qualet.irlite.domain.replay.ReplaySelections.Mode;
import qualet.irlite.domain.replay.ReplaySelections.Selection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Preserves the legacy JSON string-track format and fail-closed decoding. */
public final class ReplaySelectionCodec
{
    private ReplaySelectionCodec()
    {}

    public static Selection decode(String value)
    {
        if (value == null || value.isBlank())
        {
            return Selection.EMPTY;
        }

        try
        {
            JsonObject json = JsonParser.parseString(value).getAsJsonObject();
            Set<String> replays = new LinkedHashSet<>();
            JsonElement list = json.get("replays");

            if (list != null && list.isJsonArray())
            {
                for (JsonElement item : list.getAsJsonArray())
                {
                    String id = item.getAsString();

                    if (!id.isEmpty())
                    {
                        replays.add(id);
                    }
                }
            }

            JsonElement film = json.get("film");

            JsonElement mode = json.get("mode");
            Mode decodedMode = mode == null ? Mode.LEGACY
                : "inherit".equals(mode.getAsString()) ? Mode.INHERIT
                : "selected".equals(mode.getAsString()) ? Mode.SELECTED : Mode.LEGACY;

            return new Selection(film == null ? "" : film.getAsString(), replays, decodedMode);
        }
        catch (RuntimeException e)
        {
            /* Malformed or hand-edited: fail closed (see the class comment). */
            return Selection.EMPTY;
        }
    }

    public static String encode(String film, Collection<String> replays)
    {
        return encode(film, replays, Mode.LEGACY);
    }

    public static String encode(String film, Collection<String> replays, Mode mode)
    {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();

        for (String id : new LinkedHashSet<>(replays))
        {
            if (!id.isEmpty())
            {
                array.add(id);
            }
        }

        json.addProperty("film", film == null ? "" : film);
        json.add("replays", array);

        if (mode != Mode.LEGACY)
        {
            json.addProperty("mode", mode == Mode.INHERIT ? "inherit" : "selected");
        }

        return json.toString();
    }

}
