package Library.Repository;

import Library.Model.BookReview;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryBookReviewRepository implements BookReviewRepository, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, BookReview> data = new ConcurrentHashMap<>();

    private static String keyOf(String username, String bookId) {
        return String.valueOf(username) + "::" + String.valueOf(bookId);
    }

    @Override
    public void save(BookReview review) {
        if (review == null) {
            return;
        }
        data.put(keyOf(review.getUsername(), review.getBookId()), review);
    }

    @Override
    public Optional<BookReview> findById(String id) {
        return data.values().stream()
                .filter(review -> review.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<BookReview> findByUsernameAndBookId(String username, String bookId) {
        return Optional.ofNullable(data.get(keyOf(username, bookId)));
    }

    @Override
    public List<BookReview> findByBookId(String bookId) {
        return data.values().stream()
                .filter(review -> review.getBookId().equals(bookId))
                .toList();
    }

    @Override
    public List<BookReview> findByUsername(String username) {
        return data.values().stream()
                .filter(review -> review.getUsername().equals(username))
                .toList();
    }

    @Override
    public List<BookReview> findAll() {
        return new ArrayList<>(data.values());
    }
}