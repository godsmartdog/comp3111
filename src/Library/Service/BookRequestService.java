package Library.Service;

import Library.Exception.NotFoundException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookRequest2;
import Library.Model.BookRequestStatus;
import Library.Repository.BookRepository;
import Library.Repository.BookRequestRepository2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class BookRequestService {
    private final BookRequestRepository2 requestRepository;
    private final BookRepository bookRepository;

    public record CountItem(String name, int count) {}

    public record RequestStats(int totalRequests,
                               List<CountItem> topGenres,
                               List<CountItem> topAuthors,
                               Map<String, Integer> totalByStatus) {}

    private record CounterEntry(String displayName, int count) {}

    public BookRequestService(BookRequestRepository2 requestRepository, BookRepository bookRepository) {
        this.requestRepository = requestRepository;
        this.bookRepository = bookRepository;
    }

    public BookRequest2 submitRequest(String requesterUsername,
                                      String requesterFullName,
                                      String title,
                                      String authorName,
                                      String genres,
                                      String reason) {
        String normalizedTitle = requireNonBlank(title, "Title");
        String normalizedAuthorName = requireNonBlank(authorName, "Author");
        String normalizedReason = requireNonBlank(reason, "Reason for Request");
        List<String> normalizedGenres = parseGenres(genres);
        if (normalizedGenres.isEmpty()) {
            throw new ValidationException("Genre cannot be empty.");
        }
        ensureNoDuplicateActiveRequest(requesterUsername, normalizedTitle, normalizedAuthorName);

        BookRequest2 request = new BookRequest2(
                normalizedTitle,
                requesterUsername,
                requesterFullName,
                normalizedAuthorName,
                normalizedGenres,
                normalizedReason
        );
        requestRepository.save(request);
        return request;
    }

    public BookRequest2 setPriority(String requestId, boolean priority) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        request.setPriority(priority);
        requestRepository.save(request);
        return request;
    }

    public List<BookRequest2> listRequests() {
        return requestRepository.findAll().stream()
                .sorted(prioritySortComparator())
                .collect(Collectors.toList());
    }

    public List<BookRequest2> listRequestsByRequester(String username) {
        return requestRepository.findByRequesterUsername(username).stream()
                .sorted(prioritySortComparator())
                .collect(Collectors.toList());
    }

    public List<BookRequest2> listRequestsByStatus(BookRequestStatus status) {
        return requestRepository.findByStatus(status).stream()
                .sorted(prioritySortComparator())
                .collect(Collectors.toList());
    }

    public RequestStats getRequestStats() {
        List<BookRequest2> requests = requestRepository.findAll();
        Map<String, Integer> genreCounts = new LinkedHashMap<>();
        Map<String, String> genreLabels = new LinkedHashMap<>();
        Map<String, Integer> authorCounts = new LinkedHashMap<>();
        Map<String, String> authorLabels = new LinkedHashMap<>();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();

        for (BookRequestStatus status : BookRequestStatus.values()) {
            statusCounts.put(status.name(), 0);
        }

        for (BookRequest2 request : requests) {
            String status = request.getStatus() == null ? "UNKNOWN" : request.getStatus().name();
            statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);

            for (String genre : request.getGenres()) {
                incrementCounter(genreCounts, genreLabels, genre);
            }
            incrementCounter(authorCounts, authorLabels, request.getAuthorName());
        }

        return new RequestStats(
                requests.size(),
                topCountItems(genreCounts, genreLabels, 10),
                topCountItems(authorCounts, authorLabels, 10),
                statusCounts
        );
    }

    private static Comparator<BookRequest2> prioritySortComparator() {
        return Comparator
                .comparing((BookRequest2 r) -> !r.isPriority())
                .thenComparing(BookRequest2::getRequestedDate, Comparator.reverseOrder())
                .thenComparing(BookRequest2::getId);
    }

    public BookRequest2 getRequestByIdForReview(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ValidationException("Request ID is required.");
        }
        return requestRepository.findById(requestId.trim())
                .orElseThrow(() -> new NotFoundException("Book request not found."));
    }

    public BookRequest2 approveRequest(String requestId, String comment) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.PENDING) {
            throw new ValidationException("Only pending requests can be approved.");
        }
        request.approve(comment == null ? "Approved" : comment.trim());
        requestRepository.save(request);
        return request;
    }

    public BookRequest2 rejectRequest(String requestId, String comment, String rejectionReason) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.PENDING) {
            throw new ValidationException("Only pending requests can be rejected.");
        }
        request.reject(comment == null ? "" : comment.trim(), rejectionReason == null ? "" : rejectionReason.trim());
        requestRepository.save(request);
        return request;
    }

    public BookRequest2 uploadRequest(String requestId, String comment) {
        return uploadRequest(requestId, comment, null, "", "");
    }

    public BookRequest2 uploadRequest(String requestId,
                                      String comment,
                                      String summary,
                                      String filePath,
                                      String contentType) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.APPROVED) {
            throw new ValidationException("Only approved requests can be uploaded.");
        }

        String resolvedSummary = (summary == null || summary.isBlank())
                ? request.getReason()
                : summary.trim();

        Book book = new Book(
                request.getTitle(),
                "",
                request.getAuthorName(),
                request.getGenres(),
                resolvedSummary
        );
        if (filePath != null && !filePath.isBlank()) {
            book.setFileMetadata(filePath.trim(), contentType == null ? "" : contentType.trim());
        }
        book.approve(LocalDate.now());
        bookRepository.save(book);

        request.markUploaded(comment == null ? "Uploaded" : comment.trim(), book.getId());
        requestRepository.save(request);
        return request;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new ValidationException(fieldName + " cannot be empty.");
        }
        return value.trim();
    }

    private static List<String> parseGenres(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> genres = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                genres.add(trimmed);
            }
        }
        return genres;
    }

    private void ensureNoDuplicateActiveRequest(String requesterUsername, String title, String authorName) {
        String requestedTitle = normalizeDuplicateKey(title);
        String requestedAuthor = normalizeDuplicateKey(authorName);
        String incomingRequester = normalizeDuplicateKey(requesterUsername);
        for (BookRequest2 existing : requestRepository.findAll()) {
            if (!incomingRequester.equals(normalizeDuplicateKey(existing.getRequesterUsername()))) {
                continue;
            }
            if (!isActiveDuplicateStatus(existing.getStatus())) {
                continue;
            }
            if (requestedTitle.equals(normalizeDuplicateKey(existing.getTitle()))
                    && requestedAuthor.equals(normalizeDuplicateKey(existing.getAuthorName()))) {
                throw new ValidationException("You already submitted a request for this book.");
            }
        }
    }

    private static boolean isActiveDuplicateStatus(BookRequestStatus status) {
        return status == BookRequestStatus.PENDING
                || status == BookRequestStatus.APPROVED
                || status == BookRequestStatus.UPLOADED;
    }

    private static String normalizeDuplicateKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static void incrementCounter(Map<String, Integer> counts,
                                         Map<String, String> labels,
                                         String rawName) {
        String displayName = rawName == null ? "" : rawName.trim();
        if (displayName.isEmpty()) {
            return;
        }
        String key = displayName.toLowerCase(Locale.ROOT);
        labels.putIfAbsent(key, displayName);
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static List<CountItem> topCountItems(Map<String, Integer> counts,
                                                Map<String, String> labels,
                                                int limit) {
        return counts.entrySet().stream()
                .map(entry -> new CounterEntry(labels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .sorted(Comparator
                        .comparingInt(CounterEntry::count).reversed()
                        .thenComparing(CounterEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(entry -> new CountItem(entry.displayName(), entry.count()))
                .collect(Collectors.toList());
    }
}