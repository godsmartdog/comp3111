package Library.Service;

import Library.Exception.BusinessException;
import Library.Exception.NotFoundException;
import Library.Service.NotificationService;
import Library.Model.Book;
import Library.Model.BookReview;
import Library.Repository.BookReviewRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

        BookReview review = reviewRepository.findByUsernameAndBookId(normalizedUsername, normalizedBookId)
                .map(existing -> {
                    existing.update(rating, reviewText);
                    return existing;
                })
                .orElseGet(() -> new BookReview(normalizedUsername, normalizedBookId, rating, reviewText));

        reviewRepository.save(review);
        return review;
    }

    public List<BookReview> listReviewsForBook(String bookId) {
        String normalizedBookId = normalize(bookId);
        return reviewRepository.findByBookId(normalizedBookId).stream()
                .sorted(reviewComparator())
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
                    Map.of(
                            "type", "review-reply",
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

    private static void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}