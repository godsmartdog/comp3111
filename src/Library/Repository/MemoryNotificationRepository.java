package Library.Repository;

import Library.Model.NotificationItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class MemoryNotificationRepository implements NotificationRepository {
    private final List<NotificationItem> items = new CopyOnWriteArrayList<>();

    @Override
    public void save(NotificationItem item) {
        Optional<NotificationItem> existing = findById(item.getId());
        if (existing.isEmpty()) {
            items.add(item);
        }
    }

    @Override
    public Optional<NotificationItem> findById(String id) {
        return items.stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<NotificationItem> findByUsername(String username) {
        List<NotificationItem> result = new ArrayList<>();
        for (NotificationItem item : items) {
            if (item.getUsername().equals(username)) {
                result.add(item);
            }
        }
        return result;
    }

    @Override
    public boolean deleteById(String id) {
        return items.removeIf(item -> item.getId().equals(id));
    }
}
