package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.NotificationPriority;
import Library.Service.NotificationService;
import Library.Model.Book;
import Library.Model.BookReview;
import Library.Repository.BookReviewRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;

public class BookReviewService {
    public record RatingSummary(double averageRating, int reviewCount) {
    }

    private final BookReviewRepository reviewRepository;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final NotificationService notificationService;

    public BookReviewService(BookReviewRepository reviewRepository,
                             BookService bookService,
                             BorrowService borrowService) {
        this(reviewRepository, bookService, borrowService, null);
    }

    public BookReviewService(BookReviewRepository reviewRepository,
                             BookService bookService,
                             BorrowService borrowService,
                             NotificationService notificationService) {
        this.reviewRepository = reviewRepository;
        this.bookService = bookService;
        this.borrowService = borrowService;
        this.notificationService = notificationService;
    }

    public BookReview submitReview(String username, String bookId, int rating, String reviewText) {
        return submitReview(username, bookId, rating, reviewText, false);
    }

    public BookReview submitReview(String username, String bookId, int rating, String reviewText, boolean anonymous) {
        String normalizedUsername = normalize(username);
        String normalizedBookId = normalize(bookId);
        validateRating(rating);

        Book book = bookService.findBookById(normalizedBookId)
                .orElseThrow(() -> new NotFoundException("Book not found."));
        if (!book.isApproved()) {
            throw new BusinessException("Only approved books can be reviewed.");
        }
        if (!hasBorrowedBook(normalizedUsername, normalizedBookId)) {
            throw new BusinessException("You can only review books you have borrowed.");
        }

        final boolean anonymousFlag = anonymous;
        BookReview review = reviewRepository.findByUsernameAndBookId(normalizedUsername, normalizedBookId)
                .map(existing -> {
                    existing.update(rating, reviewText, anonymousFlag);
                    return existing;
                })
                .orElseGet(() -> new BookReview(normalizedUsername, normalizedBookId, rating, reviewText, anonymousFlag));

        // Classify sentiment of the review text using local GGUF model if available.
        try {
            String sentiment = classifySentiment(review.getReviewText());
            if (sentiment != null && !sentiment.isBlank()) {
                review.setSentiment(sentiment);
            }
        } catch (Exception e) {
            // Don't fail review submission if sentiment inference fails; log and continue.
            System.err.println("[Sentiment] Classification failed: " + e.getMessage());
        }

        reviewRepository.save(review);
        return review;
    }

    private static final String GGUF_MODEL_PATH_ENV = "GGUF_MODEL_PATH";

    private String resolveGgufModelPath() throws IOException {
        String configured = System.getenv(GGUF_MODEL_PATH_ENV);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        Path defaultPath = Paths.get(System.getProperty("user.dir"), "Library", "SmolLM2-135M-Instruct-Q3_K_XL.gguf");
        if (Files.exists(defaultPath)) {
            return defaultPath.toString();
        }
        throw new IOException("GGUF model not found. Set " + GGUF_MODEL_PATH_ENV + " to the model path.");
    }

    private String classifySentiment(String text) throws Exception {
        if (text == null || text.isBlank()) return "";
        String prompt = "\"" + text.trim() + "\" is this sentence positive, neutral or negative? Return only one word: positive, neutral, or negative.";
        String modelPath = resolveGgufModelPath();
        ModelParameters modelParameters = new ModelParameters().setModel(modelPath);
        InferenceParameters inferParams = new InferenceParameters(prompt)
                .setTemperature(0.0f)
                .setTopP(0.3f)
                .setTopK(10)
                .setRepeatPenalty(1.1f)
                .setStopStrings("\n", "\n\n");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            try (LlamaModel model = new LlamaModel(modelParameters)) {
                return model.complete(inferParams);
            }
        });

        try {
            String result = future.get(2500, TimeUnit.MILLISECONDS);
            if (result == null) return "";
            String normalized = result.trim().toLowerCase();
            if (normalized.contains("positive")) return "positive";
            if (normalized.contains("neutral")) return "neutral";
            if (normalized.contains("negative")) return "negative";
            // fallback: try first token
            String first = normalized.split("\\s+")[0].replaceAll("[^a-z]", "");
            if (first.equals("positive") || first.equals("neutral") || first.equals("negative")) return first;
            return "";
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            future.cancel(true);
            throw e;
        } finally {
            executor.shutdownNow();
        }
    }

    public List<BookReview> listReviewsForBook(String bookId) {
        String normalizedBookId = normalize(bookId);
        return reviewRepository.findByBookId(normalizedBookId).stream()
                .sorted(reviewComparator())
                .collect(Collectors.toList());
    }

    public List<BookReview> listReviewsForBook(String bookId, String sort) {
        String normalizedBookId = normalize(bookId);
        return reviewRepository.findByBookId(normalizedBookId).stream()
                .sorted(reviewComparatorForSort(sort))
                .collect(Collectors.toList());
    }

    public List<BookReview> listReviewsByUser(String username) {
        String normalizedUsername = normalize(username);
        return reviewRepository.findByUsername(normalizedUsername).stream()
                .sorted(reviewComparator())
                .collect(Collectors.toList());
    }

    public List<BookReview> listReviewsForAuthor(String authorUsername) {
        String normalizedAuthorUsername = normalize(authorUsername);
        return bookService.getBookRepository().findAll().stream()
                .filter(Book::isApproved)
                .filter(book -> normalizedAuthorUsername.equals(book.getAuthorUsername()))
                .flatMap(book -> reviewRepository.findByBookId(book.getId()).stream())
                .sorted(reviewComparator())
                .collect(Collectors.toList());
    }

    public Optional<BookReview> findReviewByUserAndBook(String username, String bookId) {
        return reviewRepository.findByUsernameAndBookId(normalize(username), normalize(bookId));
    }

    public BookReview replyToReview(String authorUsername, String reviewId, String replyText) {
        BookReview review = requireOwnedReview(authorUsername, reviewId);
        String normalizedReply = normalize(replyText);
        if (normalizedReply.isEmpty()) {
            throw new BusinessException("Reply cannot be empty.");
        }

        review.reply(normalizedReply);
        reviewRepository.save(review);

        if (notificationService != null) {
            Book book = bookService.findBookById(review.getBookId())
                    .orElseThrow(() -> new NotFoundException("Book not found."));
                notificationService.addNotification(
                    review.getUsername(),
                    "Reply to your review",
                    "Author replied to your review for \"" + book.getTitle() + "\": " + normalizedReply,
                    NotificationPriority.NORMAL,
                    null,
                    Map.of(
                        "type", "review",
                        "bookId", book.getId(),
                        "reviewId", review.getId(),
                        "authorUsername", normalize(authorUsername)
                    )
                );
        }

        return review;
    }

    public BookReview flagReview(String authorUsername, String reviewId, String reason) {
        BookReview review = requireOwnedReview(authorUsername, reviewId);
        review.flag(reason);
        reviewRepository.save(review);
        return review;
    }

    public RatingSummary getRatingSummary(String bookId) {
        List<BookReview> reviews = listReviewsForBook(bookId);
        if (reviews.isEmpty()) {
            return new RatingSummary(0.0, 0);
        }

        double total = 0.0;
        for (BookReview review : reviews) {
            total += review.getRating();
        }
        return new RatingSummary(total / reviews.size(), reviews.size());
    }

    private boolean hasBorrowedBook(String username, String bookId) {
        return borrowService.listBorrowsByUser(username).stream()
                .anyMatch(record -> record.getBookId().equals(bookId));
    }

    private BookReview requireOwnedReview(String authorUsername, String reviewId) {
        String normalizedAuthorUsername = normalize(authorUsername);
        String normalizedReviewId = normalize(reviewId);
        if (normalizedAuthorUsername.isEmpty()) {
            throw new BusinessException("Author username cannot be empty.");
        }
        if (normalizedReviewId.isEmpty()) {
            throw new BusinessException("Review ID cannot be empty.");
        }

        BookReview review = reviewRepository.findById(normalizedReviewId)
                .orElseThrow(() -> new NotFoundException("Review not found."));
        Book book = bookService.findBookById(review.getBookId())
                .orElseThrow(() -> new NotFoundException("Book not found."));
        if (!normalizedAuthorUsername.equals(book.getAuthorUsername())) {
            throw new BusinessException("Cannot manage another author's review.");
        }
        if (!book.isApproved()) {
            throw new BusinessException("Only published book reviews can be managed.");
        }
        return review;
    }

    private static Comparator<BookReview> reviewComparator() {
        return Comparator.comparing(BookReview::getUpdatedAt, Comparator.reverseOrder())
                .thenComparing(BookReview::getId);
    }

    private static Comparator<BookReview> reviewComparatorForSort(String sort) {
        String normalized = sort == null ? "" : sort.trim().toLowerCase(java.util.Locale.ROOT);
        Comparator<BookReview> recentDesc = Comparator.comparing(BookReview::getCreatedAt, Comparator.reverseOrder())
                .thenComparing(BookReview::getId);
        switch (normalized) {
            case "highest":
                return Comparator.comparingInt(BookReview::getRating).reversed()
                        .thenComparing(BookReview::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(BookReview::getId);
            case "lowest":
                return Comparator.comparingInt(BookReview::getRating)
                        .thenComparing(BookReview::getCreatedAt, Comparator.reverseOrder())
                        .thenComparing(BookReview::getId);
            case "recent":
            default:
                return recentDesc;
        }
    }

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}