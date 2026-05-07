package Library.Repository;

import Library.Model.SessionSnapshot;
import java.io.Serializable;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemorySessionSnapshotRepository implements SessionSnapshotRepository, Serializable {
    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<String, SessionSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(SessionSnapshot snapshot) {
        snapshots.put(snapshot.getSessionId(), snapshot);
    }

    @Override
    public Optional<SessionSnapshot> findBySessionId(String sessionId) {
        return Optional.ofNullable(snapshots.get(sessionId));
    }

    @Override
    public boolean clearBySessionId(String sessionId) {
        return snapshots.remove(sessionId) != null;
    }
}