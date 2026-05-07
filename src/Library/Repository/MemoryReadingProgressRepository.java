package Library.Repository;

import Library.Model.ReadingProgress;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryReadingProgressRepository implements ReadingProgressRepository, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, ReadingProgress> data = new ConcurrentHashMap<>();

    private static String keyOf(String username, String bookId) {
        return username + "::" + bookId;
    }

    @Override
    public void save(ReadingProgress progress) {
        data.put(keyOf(progress.getUsername(), progress.getBookId()), progress);
    }

    @Override
    public Optional<ReadingProgress> findByUsernameAndBookId(String username, String bookId) {
        return Optional.ofNullable(data.get(keyOf(username, bookId)));
    }
}
