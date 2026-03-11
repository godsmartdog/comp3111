package Library.Repository;
//class of borrowRecord
import Library.Model.BorrowRecord;
//list to store collections
import java.util.ArrayList;
import java.util.List;
//safe return
import java.util.Optional;
//we return this
import java.util.stream.Collectors;

public class MemoryBorrowRepository implements BorrowRepository {
    private final List<BorrowRecord> records = new ArrayList<>();
    //add record in records
    @Override
    public void save(BorrowRecord record) {
        records.add(record);
    }
    //just search the first match one
    @Override
    public Optional<BorrowRecord> findById(String id) {
        return records.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }
    //all match name will be shown
    @Override
    public List<BorrowRecord> findByUsername(String username) {
        return records.stream()
                .filter(r -> r.getUsername().equals(username))
                .collect(Collectors.toList());
    }
    @Override
    public Optional<BorrowRecord> findActiveByUsernameAndBookId(String username, String bookId) {
        return records.stream()
                .filter(r -> r.getUsername().equals(username))
                .filter(r -> r.getBookId().equals(bookId))
                .filter(r -> !r.isReturned())
                .findFirst();
    }
    //everything will be shown
    @Override
    public List<BorrowRecord> findAll() {
        return new ArrayList<>(records);
    }
}
