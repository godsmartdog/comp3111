package Library.Service;

import Library.Exception.BusinessException;
import Library.Model.NotificationAction;
import Library.Model.NotificationItem;
import Library.Repository.NotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final Map<String, Set<String>> readNotificationIdsByUser = new ConcurrentHashMap<>();

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationItem> listByUser(String username) {
        ensureDefaultNotification(username);
        List<NotificationItem> items = new ArrayList<>(notificationRepository.findByUsername(username));
        Set<String> readIds = readNotificationIdsByUser.getOrDefault(username, Set.of());
        for (NotificationItem item : items) {
            if (item.isRead() || readIds.contains(item.getId())) {
                item.markRead();
            }
        }
        items.sort(Comparator.comparing(NotificationItem::getCreatedAt).reversed());
        return items;
    }

    public List<NotificationItem> listUnreadByUser(String username) {
        List<NotificationItem> items = listByUser(username);
        List<NotificationItem> unread = new ArrayList<>();
        for (NotificationItem item : items) {
            if (!item.isRead()) {
                unread.add(item);
            }
        }
        return unread;
    }

    public NotificationItem addNotification(String username, String title, String message) {
        return addNotification(username, title, message, null, Map.of());
    }

    public NotificationItem addNotification(String username,
                                            String title,
                                            String message,
                                            NotificationAction action,
                                            Map<String, String> metadata) {
        NotificationItem item = new NotificationItem(
                username,
                safeTitle(title),
                safeMessage(message),
                LocalDateTime.now(),
                action,
                metadata
        );
        notificationRepository.save(item);
        return item;
    }

    public NotificationItem markAsRead(String username, String notificationId) {
        NotificationItem item = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!item.getUsername().equals(username)) {
            throw new BusinessException("Notification does not belong to this user.");
        }
        readNotificationIdsByUser
                .computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet())
                .add(item.getId());
        item.markRead();
        notificationRepository.save(item);
        return item;
    }

    private void ensureDefaultNotification(String username) {
        Set<String> trackedReadIds = readNotificationIdsByUser.get(username);
        if (trackedReadIds != null && trackedReadIds.isEmpty()) {
            readNotificationIdsByUser.remove(username);
        }

        if (trackedReadIds != null) {
            Set<String> validIds = new HashSet<>();
            for (NotificationItem item : notificationRepository.findByUsername(username)) {
                validIds.add(item.getId());
            }
            trackedReadIds.retainAll(validIds);
            if (trackedReadIds.isEmpty()) {
                readNotificationIdsByUser.remove(username);
            }
        }

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
