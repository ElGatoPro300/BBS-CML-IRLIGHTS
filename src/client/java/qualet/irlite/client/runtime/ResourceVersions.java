package qualet.irlite.client.runtime;

import java.util.concurrent.atomic.AtomicLong;

/** Monotonic resource revisions survive world changes to avoid cache-key collisions. */
public final class ResourceVersions
{
    private final AtomicLong sequence = new AtomicLong();
    private volatile long reload = this.next();

    public long next()
    {
        return this.sequence.incrementAndGet();
    }

    public long reloadVersion()
    {
        return this.reload;
    }

    public void reloaded()
    {
        this.reload = this.next();
    }
}
