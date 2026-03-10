package Library.Repository;

import Library.Model.BorrowRecord;
import java.util.List;
import java.util.Optional;

public interface BorrowRepository {
    void save(BorrowRecord record);
    Optional<BorrowRecord> findById(String id); // ID should be `String` to match BorrowRecord
    List<BorrowRecord> findByUsername(String username);
    List<BorrowRecord> findAll();
}
