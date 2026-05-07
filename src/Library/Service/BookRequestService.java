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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class BookRequestService {
    private static final Set<String> TINY_STOPWORDS = Set.of(
            "a", "an", "the", "of", "and", "or", "to", "in",
            "test", "manual", "smoke", "analytics", "duplicate", "guard", "book", "request", "section", "author",
            "strict", "demo", "workflow", "notify", "cleanup", "alpha", "browser", "upload"
    );
    private final BookRequestRepository2 requestRepository;
    private final BookRepository bookRepository;

    public record CountItem(String name, int count) {}

    public record RequestStats(int totalRequests,
                               List<CountItem> topGenres,
                               List<CountItem> topAuthors,
                               Map<String, Integer> totalByStatus) {}

    public record DownloadStats(DownloadStatsSummary summary,
                                List<DownloadedBookStatsItem> books,
                                List<CountItem> topGenres,
                                List<CountItem> topAuthors,
                                List<CountItem> trend) {}

    public record DownloadStatsSummary(int totalDownloadedBooks,
                                       int uniqueBooks,
                                       int uniqueRequesters,
                                       String topGenre,
                                       String topAuthor) {}

    public record DownloadedBookStatsItem(String bookId,
                                          String title,
                                          String author,
                                          List<String> genres,
                                          int downloadCount,
                                          int requestCount,
                                          LocalDate uploadedDate,
                                          List<String> requesters) {}

    public record RequestBookMatch(BookRequest2 request,
                                   int score,
                                   boolean exactTitleMatch,
                                   boolean eligible,
                                   List<String> reasons) {}

    private record CounterEntry(String displayName, int count) {}

    private static final class DownloadStatsAccumulator {
        private final String bookId;
        private String title;
        private String author;
        private List<String> genres;
        private int downloadCount;
        private int requestCount;
        private LocalDate uploadedDate;
        private final Set<String> requesters = new HashSet<>();

        private DownloadStatsAccumulator(String bookId, String title, String author, List<String> genres) {
            this.bookId = bookId;
            this.title = title == null ? "" : title.trim();
            this.author = author == null ? "" : author.trim();
            this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
        }
    }

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

    public DownloadStats getDownloadStats() {
        Map<String, DownloadStatsAccumulator> byBook = new LinkedHashMap<>();
        Map<String, Integer> genreCounts = new LinkedHashMap<>();
        Map<String, String> genreLabels = new LinkedHashMap<>();
        Map<String, Integer> authorCounts = new LinkedHashMap<>();
        Map<String, String> authorLabels = new LinkedHashMap<>();
        Map<String, Integer> trendCounts = new LinkedHashMap<>();
        Map<String, String> trendLabels = new LinkedHashMap<>();
        Set<String> uniqueRequesters = new HashSet<>();
        int totalDownloadedBooks = 0;

        for (BookRequest2 request : requestRepository.findAll()) {
            String bookId = request.getBookId() == null ? "" : request.getBookId().trim();
            if (request.getStatus() != BookRequestStatus.UPLOADED || bookId.isEmpty()) {
                continue;
            }

            Book resolvedBook = bookRepository.findById(bookId).orElse(null);
            String title = resolvedBook == null ? request.getTitle() : resolvedBook.getTitle();
            String author = resolvedBook == null ? request.getAuthorName() : resolvedBook.getAuthorFullName();
            List<String> genres = resolvedBook == null ? request.getGenres() : resolvedBook.getGenres();

            DownloadStatsAccumulator item = byBook.computeIfAbsent(
                    bookId,
                    id -> new DownloadStatsAccumulator(id, title, author, genres)
            );
            item.title = chooseDisplayValue(item.title, title);
            item.author = chooseDisplayValue(item.author, author);
            if (item.genres.isEmpty() && genres != null) {
                item.genres = new ArrayList<>(genres);
            }
            item.downloadCount++;
            item.requestCount++;
            item.requesters.add(request.getRequesterUsername());
            item.uploadedDate = latestDate(item.uploadedDate, request.getUploadedDate());

            totalDownloadedBooks++;
            if (request.getRequesterUsername() != null && !request.getRequesterUsername().isBlank()) {
                uniqueRequesters.add(request.getRequesterUsername().trim());
            }
            incrementCounter(authorCounts, authorLabels, author);
            for (String genre : genres) {
                incrementCounter(genreCounts, genreLabels, genre);
            }
            String trendDate = request.getUploadedDate() == null ? "Unknown" : request.getUploadedDate().toString();
            incrementCounter(trendCounts, trendLabels, trendDate);
        }

        List<DownloadedBookStatsItem> books = byBook.values().stream()
                .map(item -> new DownloadedBookStatsItem(
                        item.bookId,
                        item.title,
                        item.author,
                        item.genres,
                        item.downloadCount,
                        item.requestCount,
                        item.uploadedDate,
                        item.requesters.stream()
                                .filter(value -> value != null && !value.isBlank())
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .collect(Collectors.toList())
                ))
                .sorted(Comparator
                        .comparingInt(DownloadedBookStatsItem::downloadCount).reversed()
                        .thenComparing(DownloadedBookStatsItem::uploadedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DownloadedBookStatsItem::title, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        List<CountItem> topGenres = topCountItems(genreCounts, genreLabels, 10);
        List<CountItem> topAuthors = topCountItems(authorCounts, authorLabels, 10);
        List<CountItem> trend = trendCounts.entrySet().stream()
                .map(entry -> new CounterEntry(trendLabels.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(CounterEntry::displayName))
                .map(entry -> new CountItem(entry.displayName(), entry.count()))
                .collect(Collectors.toList());

        DownloadStatsSummary summary = new DownloadStatsSummary(
                totalDownloadedBooks,
                books.size(),
                uniqueRequesters.size(),
                topGenres.isEmpty() ? "" : topGenres.get(0).name(),
                topAuthors.isEmpty() ? "" : topAuthors.get(0).name()
        );
        return new DownloadStats(summary, books, topGenres, topAuthors, trend);
    }

    public List<RequestBookMatch> findSimilarOpenRequestsForBook(Book book) {
        if (book == null || !book.isApproved() || !book.isAvailable()) {
            return List.of();
        }
        List<RequestBookMatch> matches = new ArrayList<>();
        for (BookRequest2 request : requestRepository.findAll()) {
            if (!shouldConsiderForAvailableBook(request, book)) {
                continue;
            }
            RequestBookMatch match = scoreRequestBookMatch(request, book);
            if (match.eligible()) {
                matches.add(match);
            }
        }
        matches.sort(Comparator
                .comparingInt(RequestBookMatch::score).reversed()
                .thenComparing(match -> match.request().getTitle(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(match -> match.request().getId()));
        return matches;
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

    private static String chooseDisplayValue(String current, String candidate) {
        String normalizedCandidate = candidate == null ? "" : candidate.trim();
        return normalizedCandidate.isEmpty() ? (current == null ? "" : current) : normalizedCandidate;
    }

    private static LocalDate latestDate(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null || candidate.isAfter(current)) {
            return candidate;
        }
        return current;
    }

    private static boolean shouldConsiderForAvailableBook(BookRequest2 request, Book book) {
        if (request == null || request.getStatus() == BookRequestStatus.REJECTED) {
            return false;
        }
        if (request.getStatus() == BookRequestStatus.UPLOADED) {
            return book.getId().equals(request.getBookId());
        }
        return request.getStatus() == BookRequestStatus.PENDING
                || request.getStatus() == BookRequestStatus.APPROVED;
    }

    private static RequestBookMatch scoreRequestBookMatch(BookRequest2 request, Book book) {
        int score = 0;
        boolean exactTitle = false;
        boolean titleSubstring = false;
        List<String> reasons = new ArrayList<>();

        String requestTitle = normalizeDuplicateKey(request.getTitle());
        String bookTitle = normalizeDuplicateKey(book.getTitle());
        Set<String> requestTitleTokens = tokenize(request.getTitle());
        Set<String> bookTitleTokens = tokenize(book.getTitle());
        int meaningfulSharedTitleTokens = countSharedTokens(requestTitleTokens, bookTitleTokens);
        if (!requestTitle.isEmpty() && requestTitle.equals(bookTitle)) {
            score += 80;
            exactTitle = true;
            addReason(reasons, "Exact title match");
        } else if (!requestTitle.isEmpty() && !bookTitle.isEmpty()
                && (requestTitle.contains(bookTitle) || bookTitle.contains(requestTitle))
                && hasMeaningfulTitleSubstring(requestTitleTokens, bookTitleTokens, meaningfulSharedTitleTokens)) {
            score += 50;
            titleSubstring = true;
            addReason(reasons, "Similar title");
        }

        if (meaningfulSharedTitleTokens > 0) {
            score += 20 * meaningfulSharedTitleTokens;
            addReason(reasons, "Similar title");
        }

        boolean authorMatch = false;
        String requestAuthor = normalizeDuplicateKey(request.getAuthorName());
        String bookAuthor = normalizeDuplicateKey(book.getAuthorFullName());
        if (!requestAuthor.isEmpty() && requestAuthor.equals(bookAuthor)) {
            score += 35;
            authorMatch = true;
            addReason(reasons, "Same author");
        } else if (!requestAuthor.isEmpty() && !bookAuthor.isEmpty()
                && hasMeaningfulAuthorSubstring(requestAuthor, bookAuthor)) {
            score += 15;
            authorMatch = true;
            addReason(reasons, "Similar author");
        }

        int sharedGenres = 0;
        Set<String> requestGenres = normalizeGenreSet(request.getGenres());
        for (String genre : book.getGenres()) {
            if (requestGenres.contains(normalizeDuplicateKey(genre))) {
                sharedGenres++;
                score += 20;
                addReason(reasons, "Shared genre: " + genre);
            }
        }

        boolean strongTitleMatch = titleSubstring || meaningfulSharedTitleTokens >= 2;
        boolean eligible = exactTitle
                || strongTitleMatch
                || (authorMatch && (sharedGenres > 0 || meaningfulSharedTitleTokens > 0));

        return new RequestBookMatch(request, score, exactTitle, eligible, reasons);
    }

    private static Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        for (String part : value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (isMeaningfulToken(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static boolean isMeaningfulToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.length() <= 2) {
            return false;
        }
        if (token.matches("\\d+")) {
            return false;
        }
        return !TINY_STOPWORDS.contains(token);
    }

    private static boolean hasMeaningfulTitleSubstring(Set<String> leftTokens, Set<String> rightTokens, int sharedCount) {
        if (sharedCount <= 0 || leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return false;
        }
        return sharedCount >= Math.min(leftTokens.size(), rightTokens.size());
    }

    private static boolean hasMeaningfulAuthorSubstring(String leftAuthor, String rightAuthor) {
        String shorter = leftAuthor.length() <= rightAuthor.length() ? leftAuthor : rightAuthor;
        String longer = leftAuthor.length() <= rightAuthor.length() ? rightAuthor : leftAuthor;
        if (shorter.length() < 4 || !longer.contains(shorter)) {
            return false;
        }
        Set<String> shorterTokens = tokenize(shorter);
        return !shorterTokens.isEmpty();
    }

    private static Set<String> normalizeGenreSet(List<String> genres) {
        Set<String> normalized = new HashSet<>();
        if (genres == null) {
            return normalized;
        }
        for (String genre : genres) {
            String value = normalizeDuplicateKey(genre);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static int countSharedTokens(Set<String> left, Set<String> right) {
        int shared = 0;
        for (String token : left) {
            if (right.contains(token)) {
                shared++;
            }
        }
        return shared;
    }

    private static void addReason(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) {
            reasons.add(reason);
        }
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