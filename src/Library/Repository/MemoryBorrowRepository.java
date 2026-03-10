package Library.Repository;

import Library.Model.BorrowRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MemoryBorrowRepository implements BorrowRepository {
    private final List<BorrowRecord> records = new ArrayList<>();

    @Override
    public void save(BorrowRecord record) {
        records.add(record);
    }

    @Override
    public Optional<BorrowRecord> findById(String id) {
        return records.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<BorrowRecord> findByUsername(String username) {
        return records.stream()
                .filter(r -> r.getUsername().equals(username))
                .collect(Collectors.toList());
    }

    @Override
    public List<BorrowRecord> findAll() {
        return new ArrayList<>(records);
    }
}
