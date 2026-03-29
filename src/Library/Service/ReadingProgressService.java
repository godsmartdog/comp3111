package Library.Service;

import Library.Model.ReadingProgress;
import Library.Repository.ReadingProgressRepository;
import java.util.List;

public class ReadingProgressService {
    private final ReadingProgressRepository readingProgressRepository;

    public ReadingProgressService(ReadingProgressRepository readingProgressRepository) {
        this.readingProgressRepository = readingProgressRepository;
    }

    public ReadingProgress getProgress(String username, String bookId) {
        return readingProgressRepository
                .findByUsernameAndBookId(username, bookId)
                .orElseGet(() -> {
                    ReadingProgress progress = new ReadingProgress(username, bookId);
                    readingProgressRepository.save(progress);
                    return progress;
                });
    }

    public ReadingProgress updateProgress(String username, String bookId, int bookmarkPage, List<String> highlights) {
        ReadingProgress progress = getProgress(username, bookId);
        progress.update(bookmarkPage, highlights);
        readingProgressRepository.save(progress);
        return progress;
    }
}
