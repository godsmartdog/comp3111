//just import library
package Library.Repository;
//class of bookSubmission2 and data type of SubmissionState
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
        data.removeIf(s -> s.getId().equals(submission.getId()));// because this is book submission, so we won't have same id submit, we delete if repeated
        data.add(submission);//store in list
    }

    @Override
    public void deleteById(String id) {
        data.removeIf(s -> s.getId().equals(id));
    }

    //return first match id submitmission
    @Override
    public Optional<BookSubmission2> findById(String id) {
        return data.stream().filter(s -> s.getId().equals(id)).findFirst();
    }
    //list all
    @Override
    public List<BookSubmission2> findAll() {
        return new ArrayList<>(data);
    }
    //list all submission with match status in list
    @Override
    public List<BookSubmission2> findByStatus(SubmissionState status) {
        return data.stream().filter(s -> s.getStatus() == status).collect(Collectors.toList());
    }

    //list all submission with match AuthorUsename in list
    @Override
    public List<BookSubmission2> findByAuthorUsername(String username) {
        return data.stream().filter(s -> s.getAuthorUsername().equals(username)).collect(Collectors.toList());
    }
}
