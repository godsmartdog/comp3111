package Library.Service;

import Library.Model.Book;
import Library.Repository.BookRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Service class to handle book-related operations such as listing approved books and searching for books by title or author.
public class BookService {
    private static final Set<String> TINY_STOPWORDS = Set.of("a", "an", "the", "of", "and", "or", "to", "in");
    private final BookRepository bookRepository;

    public record AlternativeBookSuggestion(Book book, int score, List<String> reasons) {}

    // Constructor to initialize the BookService with the required BookRepository, allowing for dependency injection and better separation of concerns.
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // Method to list all approved books along with their availability status, filtering the books to include only those that are approved and sorting them by title before returning the list.
    public List<Book> listApprovedBooksWithAvailability() {
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    // Method to search for approved books by title or author, filtering the search results to include only approved books and sorting them by title before returning the list.
    public List<Book> searchApprovedBooks(String keyword) {
        return bookRepository.searchByTitleOrAuthor(keyword).stream()
                .filter(Book::isApproved)
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksWithFilters(String keyword, Boolean availableFilter) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<Book> base = normalizedKeyword.isEmpty()
                ? listApprovedBooksWithAvailability()
                : searchApprovedBooks(normalizedKeyword);

        if (availableFilter == null) {
            return base;
        }

        return base.stream()
                .filter(book -> book.isAvailable() == availableFilter)
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksByAuthorUsername(String authorUsername) {
        String normalized = authorUsername == null ? "" : authorUsername.trim();
        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .filter(book -> book.getAuthorUsername().equals(normalized))
                .sorted(Comparator.comparing(Book::getTitle))
                .collect(Collectors.toList());
    }

    public List<Book> listApprovedBooksForLibrarian() {
        return listApprovedBooksWithAvailability();
    }

    public Optional<Book> findBookById(String bookId) {
        return bookRepository.findById(bookId);
    }

    public List<AlternativeBookSuggestion> suggestAlternativeBooks(String title,
                                                                   String author,
                                                                   List<String> genres,
                                                                   int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        String requestedTitle = normalizeText(title);
        String requestedAuthor = normalizeText(author);
        Set<String> requestedTitleTokens = tokenize(title);
        Set<String> requestedAuthorTokens = tokenize(author);
        Set<String> requestedGenres = normalizeGenres(genres);

        if (requestedTitle.isEmpty() && requestedAuthor.isEmpty() && requestedGenres.isEmpty()) {
            return List.of();
        }

        return bookRepository.findAll().stream()
                .filter(Book::isApproved)
                .filter(Book::isAvailable)
                .map(book -> scoreSuggestion(book, requestedTitle, requestedAuthor,
                        requestedTitleTokens, requestedAuthorTokens, requestedGenres))
                .filter(suggestion -> suggestion.score() > 0)
                .sorted(Comparator
                        .comparingInt(AlternativeBookSuggestion::score).reversed()
                        .thenComparing(suggestion -> suggestion.book().getTitle(), String.CASE_INSENSITIVE_ORDER))
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    public BookRepository getBookRepository() {
        return bookRepository;
    }

    private static AlternativeBookSuggestion scoreSuggestion(Book book,
                                                             String requestedTitle,
                                                             String requestedAuthor,
                                                             Set<String> requestedTitleTokens,
                                                             Set<String> requestedAuthorTokens,
                                                             Set<String> requestedGenres) {
        int score = 0;
        List<String> reasons = new ArrayList<>();

        String bookTitle = normalizeText(book.getTitle());
        if (!requestedTitle.isEmpty() && !bookTitle.isEmpty()
                && (bookTitle.contains(requestedTitle) || requestedTitle.contains(bookTitle))) {
            score += 60;
            addReason(reasons, "Similar title");
        }

        int sharedTitleTokens = countSharedTokens(requestedTitleTokens, tokenize(book.getTitle()));
        if (sharedTitleTokens > 0) {
            score += 20 * sharedTitleTokens;
            addReason(reasons, "Similar title");
        }

        String bookAuthor = normalizeText(book.getAuthorFullName());
        if (!requestedAuthor.isEmpty() && !bookAuthor.isEmpty()) {
            if (bookAuthor.equals(requestedAuthor)) {
                score += 30;
                addReason(reasons, "Same author");
            } else if (bookAuthor.contains(requestedAuthor)
                    || requestedAuthor.contains(bookAuthor)
                    || countSharedTokens(requestedAuthorTokens, tokenize(book.getAuthorFullName())) > 0) {
                score += 15;
                addReason(reasons, "Similar author");
            }
        }

        for (String genre : book.getGenres()) {
            if (requestedGenres.contains(normalizeText(genre))) {
                score += 20;
                addReason(reasons, "Shared genre: " + genre);
            }
        }

        if (score > 0 && book.isAvailable()) {
            score += 10;
            addReason(reasons, "Available now");
        }

        return new AlternativeBookSuggestion(book, score, reasons);
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        if (value == null || value.isBlank()) {
            return tokens;
        }
        for (String part : value.toLowerCase().split("[^a-z0-9]+")) {
            if (!part.isBlank() && !TINY_STOPWORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static Set<String> normalizeGenres(List<String> genres) {
        Set<String> normalized = new HashSet<>();
        if (genres == null) {
            return normalized;
        }
        for (String genre : genres) {
            String value = normalizeText(genre);
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
}
