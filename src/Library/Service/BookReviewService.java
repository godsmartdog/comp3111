package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Model.NotificationPriority;
import Library.Service.NotificationService;
import Library.Model.Book;
import Library.Model.BookReview;
import Library.Repository.BookReviewRepository;
import java.util.Comparator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class BookReviewService {
    public record RatingSummary(double averageRating, int reviewCount) {
    }

    private final BookReviewRepository reviewRepository;
    private final BookService bookService;
    private final BorrowService borrowService;
    private final NotificationService notificationService;

        private static final Set<String> POSITIVE_SENTIMENT_KEYWORDS = Set.of(
            "excellent", "great", "good", "helpful", "clear", "enjoyable", "useful", "amazing", "recommend", "loved"
        );
        private static final Set<String> NEGATIVE_SENTIMENT_KEYWORDS = Set.of(
            "bad", "poor", "confusing", "boring", "difficult", "unclear", "useless", "terrible", "hate", "disappointed"
        );

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

        String sentiment = classifySentiment(review.getReviewText());
        if (sentiment != null && !sentiment.isBlank()) {
            review.setSentiment(sentiment);
        }

        reviewRepository.save(review);
        return review;
    }

    private static String classifySentiment(String text) {
        if (text == null || text.isBlank()) {
            return "neutral";
        }
        int score = 0;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ");
        for (String token : normalized.split("\\s+")) {
            if (POSITIVE_SENTIMENT_KEYWORDS.contains(token)) {
                score++;
            }
            if (NEGATIVE_SENTIMENT_KEYWORDS.contains(token)) {
                score--;
            }
        }
        if (score > 0) {
            return "positive";
        }
        if (score < 0) {
            return "negative";
        }
        return "neutral";
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
        return listReviewsByUser(username, "recent");
    }

    public List<BookReview> listReviewsByUser(String username, String sort) {
        String normalizedUsername = normalize(username);
        return reviewRepository.findByUsername(normalizedUsername).stream()
                .sorted(reviewComparatorForSort(sort))
                .collect(Collectors.toList());
    }

    public BookReview markHelpful(String username, String reviewId) {
        String normalizedUsername = normalize(username);
        String normalizedReviewId = normalize(reviewId);
        if (normalizedUsername.isEmpty()) {
            throw new BusinessException("Username cannot be empty.");
        }
        BookReview review = reviewRepository.findById(normalizedReviewId)
                .orElseThrow(() -> new NotFoundException("Review not found."));
        if (normalizedUsername.equals(review.getUsername())) {
            throw new BusinessException("You cannot mark your own review as helpful.");
        }
        boolean added = review.markHelpful(normalizedUsername);
        if (!added) {
            throw new BusinessException("You already marked this review as helpful.");
        }
        reviewRepository.save(review);
        return review;
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
            case "helpful":
            case "most-helpful":
                return Comparator.comparingInt(BookReview::getHelpfulCount).reversed()
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