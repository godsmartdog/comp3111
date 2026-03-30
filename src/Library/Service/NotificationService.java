package Library.Service;

import Library.Exception.BusinessException;
import Library.Model.NotificationAction;
import Library.Model.NotificationItem;
import Library.Model.NotificationPriority;
import Library.Repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NotificationService {
    public enum NotificationScope {
        ACTIVE,
        ARCHIVED,
        ALL;

        public static NotificationScope fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return ACTIVE;
            }
            return switch (raw.trim().toLowerCase()) {
                case "active" -> ACTIVE;
                case "archived" -> ARCHIVED;
                case "all" -> ALL;
                default -> throw new BusinessException("Invalid scope. Use active, archived, or all.");
            };
        }
    }

    public enum NotificationReadFilter {
        ALL,
        READ,
        UNREAD;

        public static NotificationReadFilter fromString(String raw) {
            if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
                return ALL;
            }
            return switch (raw.trim().toLowerCase()) {
                case "read" -> READ;
                case "unread" -> UNREAD;
                default -> throw new BusinessException("Invalid read filter. Use all, read, or unread.");
            };
        }
    }

    public enum NotificationSortBy {
        CREATED_AT,
        PRIORITY;

        public static NotificationSortBy fromString(String raw) {
            if (raw == null || raw.isBlank() || "createdat".equalsIgnoreCase(raw.trim())) {
                return CREATED_AT;
            }
            return switch (raw.trim().toLowerCase()) {
                case "priority" -> PRIORITY;
                default -> throw new BusinessException("Invalid sortBy. Use createdAt or priority.");
            };
        }
    }

    public enum NotificationSortDirection {
        ASC,
        DESC;

        public static NotificationSortDirection fromString(String raw) {
            if (raw == null || raw.isBlank() || "desc".equalsIgnoreCase(raw.trim())) {
                return DESC;
            }
            return switch (raw.trim().toLowerCase()) {
                case "asc" -> ASC;
                default -> throw new BusinessException("Invalid sortDir. Use asc or desc.");
            };
        }
    }

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationItem> listByUser(String username) {
        return listByUser(username, NotificationScope.ACTIVE);
    }

    public List<NotificationItem> listByUser(String username, NotificationScope scope) {
        return listByUser(username, scope, null, NotificationReadFilter.ALL, null, NotificationSortBy.CREATED_AT, NotificationSortDirection.DESC);
    }

    public List<NotificationItem> listByUser(String username,
                                             NotificationScope scope,
                                             String keyword,
                                             NotificationReadFilter readFilter,
                                             NotificationPriority priorityFilter,
                                             NotificationSortBy sortBy,
                                             NotificationSortDirection sortDirection) {
        ensureDefaultNotification(username);
        NotificationScope effectiveScope = scope == null ? NotificationScope.ACTIVE : scope;
        NotificationReadFilter effectiveReadFilter = readFilter == null ? NotificationReadFilter.ALL : readFilter;
        NotificationSortBy effectiveSortBy = sortBy == null ? NotificationSortBy.CREATED_AT : sortBy;
        NotificationSortDirection effectiveSortDirection = sortDirection == null ? NotificationSortDirection.DESC : sortDirection;
        String normalizedKeyword = normalizeKeyword(keyword);
        List<NotificationItem> items = new ArrayList<>();
        for (NotificationItem item : notificationRepository.findByUsername(username)) {
            if (matchesScope(item, effectiveScope)
                    && matchesKeyword(item, normalizedKeyword)
                    && matchesReadFilter(item, effectiveReadFilter)
                    && matchesPriority(item, priorityFilter)) {
                items.add(item);
            }
        }
        items.sort(buildComparator(effectiveSortBy, effectiveSortDirection));
        return items;
    }

    public NotificationItem addNotification(String username, String title, String message) {
        return addNotification(username, title, message, NotificationPriority.NORMAL, null, Map.of());
    }

    public NotificationItem addNotification(String username,
                                            String title,
                                            String message,
                                            NotificationAction action,
                                            Map<String, String> metadata) {
        return addNotification(username, title, message, NotificationPriority.NORMAL, action, metadata);
    }

    public NotificationItem addNotification(String username,
                                            String title,
                                            String message,
                                            NotificationPriority priority,
                                            NotificationAction action,
                                            Map<String, String> metadata) {
        NotificationItem item = new NotificationItem(
                username,
                safeTitle(title),
                safeMessage(message),
                LocalDateTime.now(),
                priority,
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
        item.markRead();
        notificationRepository.save(item);
        return item;
    }

    public NotificationItem deleteNotification(String username, String notificationId) {
        NotificationItem item = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!item.getUsername().equals(username)) {
            throw new BusinessException("Notification does not belong to this user.");
        }

        boolean deleted = notificationRepository.deleteById(notificationId);
        if (!deleted) {
            throw new BusinessException("Notification not found.");
        }
        return item;
    }

    public NotificationItem archiveNotification(String username, String notificationId) {
        NotificationItem item = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!item.getUsername().equals(username)) {
            throw new BusinessException("Notification does not belong to this user.");
        }

        item.archive();
        notificationRepository.save(item);
        return item;
    }

    public NotificationItem unarchiveNotification(String username, String notificationId) {
        NotificationItem item = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!item.getUsername().equals(username)) {
            throw new BusinessException("Notification does not belong to this user.");
        }

        item.unarchive();
        notificationRepository.save(item);
        return item;
    }

    public boolean addBorrowReminderIfAbsent(String username,
                                             String borrowRecordId,
                                             String category,
                                             LocalDate reminderDate,
                                             LocalDate dueDate,
                                             String title,
                                             String message,
                                             NotificationPriority priority) {
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase();
        String normalizedBorrowRecordId = borrowRecordId == null ? "" : borrowRecordId.trim();
        String normalizedReminderDate = reminderDate == null ? "" : reminderDate.toString();

        for (NotificationItem item : notificationRepository.findByUsername(username)) {
            Map<String, String> metadata = item.getMetadata();
            if (normalizedBorrowRecordId.equals(metadata.getOrDefault("borrowRecordId", ""))
                    && normalizedCategory.equals(metadata.getOrDefault("category", "").toLowerCase())
                    && normalizedReminderDate.equals(metadata.getOrDefault("reminderDate", ""))) {
                return false;
            }
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("type", "borrow-reminder");
        metadata.put("category", normalizedCategory);
        metadata.put("borrowRecordId", normalizedBorrowRecordId);
        metadata.put("reminderDate", normalizedReminderDate);
        metadata.put("dueDate", dueDate == null ? "" : dueDate.toString());

        addNotification(username, title, message, priority, null, metadata);
        return true;
    }

    private static boolean matchesScope(NotificationItem item, NotificationScope scope) {
        return switch (scope) {
            case ACTIVE -> !item.isArchived();
            case ARCHIVED -> item.isArchived();
            case ALL -> true;
        };
    }

    private static boolean matchesKeyword(NotificationItem item, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        String title = safeLower(item.getTitle());
        String message = safeLower(item.getMessage());
        return title.contains(normalizedKeyword) || message.contains(normalizedKeyword);
    }

    private static boolean matchesReadFilter(NotificationItem item, NotificationReadFilter readFilter) {
        return switch (readFilter) {
            case ALL -> true;
            case READ -> item.isRead();
            case UNREAD -> !item.isRead();
        };
    }

    private static boolean matchesPriority(NotificationItem item, NotificationPriority priorityFilter) {
        return priorityFilter == null || item.getPriority() == priorityFilter;
    }

    private static Comparator<NotificationItem> buildComparator(NotificationSortBy sortBy,
                                                                NotificationSortDirection direction) {
        Comparator<NotificationItem> primary;
        if (sortBy == NotificationSortBy.PRIORITY) {
            primary = Comparator.comparingInt(item -> priorityWeight(item.getPriority()));
        } else {
            primary = Comparator.comparing(NotificationItem::getCreatedAt);
        }

        if (direction == NotificationSortDirection.DESC) {
            primary = primary.reversed();
        }

        Comparator<NotificationItem> tieBreaker = Comparator
                .comparing(NotificationItem::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(NotificationItem::getId);
        return primary.thenComparing(tieBreaker);
    }

    private static int priorityWeight(NotificationPriority priority) {
        return switch (priority == null ? NotificationPriority.NORMAL : priority) {
            case LOW -> 1;
            case NORMAL -> 2;
            case HIGH -> 3;
        };
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim().toLowerCase();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
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
