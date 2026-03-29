package Library.Service;

import Library.Model.Role;
import Library.Model.SessionSnapshot;
import Library.Repository.SessionSnapshotRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public class SessionSnapshotService {
    private final SessionSnapshotRepository sessionSnapshotRepository;

    public SessionSnapshotService(SessionSnapshotRepository sessionSnapshotRepository) {
        this.sessionSnapshotRepository = sessionSnapshotRepository;
    }

    public SessionSnapshot saveSnapshot(String sessionId,
                                        String username,
                                        Role role,
                                        String portalKey,
                                        String lastViewKey,
                                        String lastAction,
                                        String statePayload) {
        SessionSnapshot snapshot = new SessionSnapshot(
                sessionId,
                username,
                role,
                portalKey,
                lastViewKey,
                lastAction,
                LocalDateTime.now(),
                statePayload
        );
        sessionSnapshotRepository.save(snapshot);
        return snapshot;
    }

    public Optional<SessionSnapshot> getSnapshot(String sessionId, String username, Role role) {
        Optional<SessionSnapshot> snapshot = sessionSnapshotRepository.findBySessionId(sessionId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        SessionSnapshot value = snapshot.get();
        if (!value.getUsername().equals(username) || value.getRole() != role) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public boolean clearSnapshot(String sessionId, String username, Role role) {
        Optional<SessionSnapshot> snapshot = getSnapshot(sessionId, username, role);
        if (snapshot.isEmpty()) {
            return false;
        }
        return sessionSnapshotRepository.clearBySessionId(sessionId);
    }

    public boolean clearSnapshotForSession(String sessionId) {
        return sessionSnapshotRepository.clearBySessionId(sessionId);
    }
}