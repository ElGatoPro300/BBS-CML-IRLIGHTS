package qualet.irlite.domain.replay;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Session-owned replay tokens and draw ownership; runtime types are supplied by adapters. */
public final class ReplayFrameState<E, F>
{
    private static final int MAX_TOKEN = 1 << 24;

    private final Map<E, Integer> entities = new IdentityHashMap<>();
    private final Map<F, Integer> forms = new IdentityHashMap<>();
    private final Map<String, Map<String, Integer>> tokens = new HashMap<>();
    private final Map<String, Map<String, Integer>> active = new HashMap<>();
    private Object world;
    private int nextId = 1;
    private int currentId;
    private Runnable listener;

    public void beginFrame(Object world)
    {
        if (this.world != world)
        {
            this.reset();
            this.world = world;
        }

        this.entities.clear();
        this.forms.clear();
        this.active.clear();
        this.set(0);
    }

    public void reset()
    {
        this.world = null;
        this.tokens.clear();
        this.entities.clear();
        this.forms.clear();
        this.active.clear();
        this.nextId = 1;
        this.set(0);
    }

    public void register(String film, String replay, E entity, F form)
    {
        Map<String, Integer> ids = this.tokens.computeIfAbsent(film, key -> new HashMap<>());
        int token = ids.computeIfAbsent(replay, key -> this.nextId++);

        if (token >= MAX_TOKEN)
        {
            throw new IllegalStateException("IRLights: replay tag precision exhausted (" + token + ")");
        }

        this.entities.put(entity, token);

        if (form != null)
        {
            this.forms.put(form, token);
        }

        this.active.computeIfAbsent(film, key -> new HashMap<>()).put(replay, token);
    }

    public int resolve(String film, String replay)
    {
        Map<String, Integer> ids = this.active.get(film);

        return ids == null ? 0 : ids.getOrDefault(replay, 0);
    }

    public Integer entity(E entity)
    {
        return this.entities.get(entity);
    }

    public Integer form(F form)
    {
        return this.forms.get(form);
    }

    public int currentId()
    {
        return this.currentId;
    }

    public void listener(Runnable listener)
    {
        this.listener = listener;
    }

    public void set(int id)
    {
        if (id == this.currentId)
        {
            return;
        }

        this.currentId = id;

        if (this.listener != null)
        {
            this.listener.run();
        }
    }

    public Runnable capture(Runnable draw)
    {
        if (draw == null)
        {
            return null;
        }

        int captured = this.currentId;

        return () ->
        {
            int previous = this.currentId;
            this.set(captured);

            try
            {
                draw.run();
            }
            finally
            {
                this.set(previous);
            }
        };
    }
}
