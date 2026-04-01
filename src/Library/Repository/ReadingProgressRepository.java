package Library.Repository;

import Library.Model.ReadingProgress;
import java.util.Optional;

public interface ReadingProgressRepository {
    void save(ReadingProgress progress);
    Optional<ReadingProgress> findByUsernameAndBookId(String username, String bookId);
}
