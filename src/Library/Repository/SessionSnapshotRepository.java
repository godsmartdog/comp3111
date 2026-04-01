package Library.Repository;

import Library.Model.SessionSnapshot;
import java.util.Optional;

public interface SessionSnapshotRepository {
    void save(SessionSnapshot snapshot);
    Optional<SessionSnapshot> findBySessionId(String sessionId);
    boolean clearBySessionId(String sessionId);
}