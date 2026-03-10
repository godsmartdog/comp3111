package Library.Repository;

import Library.Model.BookSubmission2;
import Library.Model.SubmissionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class MemoryBookSubmissionRepository2 implements BookSubmissionRepository2 {
    private final List<BookSubmission2> data = new CopyOnWriteArrayList<>();

    @Override
    public void save(BookSubmission2 submission) {
        data.removeIf(s -> s.getId().equals(submission.getId()));
        data.add(submission);
    }

    @Override
    public Optional<BookSubmission2> findById(String id) {
        return data.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    @Override
    public List<BookSubmission2> findAll() {
        return new ArrayList<>(data);
    }

    @Override
    public List<BookSubmission2> findByStatus(SubmissionState status) {
        return data.stream().filter(s -> s.getStatus() == status).collect(Collectors.toList());
    }

    @Override
    public List<BookSubmission2> findByAuthorUsername(String username) {
        return data.stream().filter(s -> s.getAuthorUsername().equals(username)).collect(Collectors.toList());
    }
}
