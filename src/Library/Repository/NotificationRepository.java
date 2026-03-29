package Library.Repository;

import Library.Model.NotificationItem;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {
    void save(NotificationItem item);
    Optional<NotificationItem> findById(String id);
    List<NotificationItem> findByUsername(String username);
}
