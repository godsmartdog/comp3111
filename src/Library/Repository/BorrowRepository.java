//This file will be used in Library.service.BorrowService
//function defined in Memoryxxxx -> just go see this
// Import the system library used for extension handling
package Library.Repository;
//import class of BorrowRecord
import Library.Model.BorrowRecord;
//list to store collection
import java.util.List;
//safe return
import java.util.Optional;

public interface BorrowRepository {
    void save(BorrowRecord record);
    Optional<BorrowRecord> findById(String id); // ID should be `String` to match BorrowRecord
    List<BorrowRecord> findByUsername(String username);
    Optional<BorrowRecord> findActiveByUsernameAndBookId(String username, String bookId);
    List<BorrowRecord> findAll();
}
