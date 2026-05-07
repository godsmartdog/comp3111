package Library.Repository;

import Library.Model.BookRequest2;
import Library.Model.BookRequestStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MemoryBookRequestRepository2 implements BookRequestRepository2, Serializable {
    private static final long serialVersionUID = 1L;

    private final List<BookRequest2> data = new CopyOnWriteArrayList<>();

    @Override
    public void save(BookRequest2 request) {
        data.removeIf(item -> item.getId().equals(request.getId()));
        data.add(request);
    }

    @Override
    public Optional<BookRequest2> findById(String id) {
        return data.stream().filter(item -> item.getId().equals(id)).findFirst();
    }

    @Override
    public List<BookRequest2> findAll() {
        return new ArrayList<>(data);
    }

    @Override
    public List<BookRequest2> findByStatus(BookRequestStatus status) {
        return data.stream().filter(item -> item.getStatus() == status).collect(Collectors.toList());
    }

    @Override
    public List<BookRequest2> findByRequesterUsername(String username) {
        return data.stream().filter(item -> item.getRequesterUsername().equals(username)).collect(Collectors.toList());
    }
}