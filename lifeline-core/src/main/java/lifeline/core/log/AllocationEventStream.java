package lifeline.core.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory thread-safe event stream for allocation log entries.
 * Can be queried directly or consumed by database loggers.
 */
public final class AllocationEventStream {

    private final List<AllocationLogEntry> entries = new CopyOnWriteArrayList<>();

    public void append(AllocationLogEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        entries.add(entry);
    }

    public List<AllocationLogEntry> entriesForRequest(String requestId) {
        Objects.requireNonNull(requestId, "requestId cannot be null");
        List<AllocationLogEntry> filtered = new ArrayList<>();
        for (AllocationLogEntry e : entries) {
            if (requestId.equals(e.requestId())) {
                filtered.add(e);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public List<AllocationLogEntry> allEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public void clear() {
        entries.clear();
    }
}
