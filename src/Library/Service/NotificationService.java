package Library.Service;

import Library.Exception.BusinessException;
import Library.Model.NotificationItem;
import Library.Repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationItem> listByUser(String username) {
        ensureDefaultNotification(username);
        List<NotificationItem> items = new ArrayList<>(notificationRepository.findByUsername(username));
        items.sort(Comparator.comparing(NotificationItem::getCreatedAt).reversed());
        return items;
    }

    public NotificationItem addNotification(String username, String title, String message) {
        NotificationItem item = new NotificationItem(username, safeTitle(title), safeMessage(message), LocalDateTime.now());
        notificationRepository.save(item);
        return item;
    }

    public NotificationItem markAsRead(String username, String notificationId) {
        NotificationItem item = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!item.getUsername().equals(username)) {
            throw new BusinessException("Notification does not belong to this user.");
        }
        item.markRead();
        notificationRepository.save(item);
        return item;
    }

    private void ensureDefaultNotification(String username) {
        List<NotificationItem> existing = notificationRepository.findByUsername(username);
        if (!existing.isEmpty()) {
            return;
        }

        addNotification(
                username,
                "Welcome",
                "Your notification board is ready. New updates will appear here."
        );
    }

    private static String safeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            return "Notification";
        }
        return value;
    }

    private static String safeMessage(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isEmpty()) {
            return "";
        }
        return value;
    }
}
