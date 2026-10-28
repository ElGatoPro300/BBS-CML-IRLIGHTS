package qualet.irlite.domain.replay;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Stable replay identifiers and mode, independent of runtime films and entities. */
public final class ReplaySelections
{
    /** Missing mode is the original format, whose switches still live on the form. */
    public enum Mode { LEGACY, SELECTED, INHERIT }

    public record Selection(String film, Set<String> replays, Mode mode)
    {
        public Selection
        {
            replays = Collections.unmodifiableSet(new LinkedHashSet<>(replays));
        }

        public static final Selection EMPTY = new Selection("", Set.of(), Mode.LEGACY);

        public boolean isEmpty()
        {
            return this.replays.isEmpty();
        }

        /** Whether this selection was made in {@code film} (a list from another film matches nobody). */
        public boolean belongsTo(String film)
        {
            return film != null && this.film.equals(film);
        }
    }

    private ReplaySelections()
    {}
}
