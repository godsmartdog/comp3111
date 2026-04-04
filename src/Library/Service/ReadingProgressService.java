package Library.Service;

import Library.Model.ReadingProgress;
import Library.Repository.ReadingProgressRepository;
import java.util.Optional;
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

    public Optional<ReadingProgress> findProgress(String username, String bookId) {
        return readingProgressRepository.findByUsernameAndBookId(username, bookId);
    }

    public ReadingProgress updateProgress(String username, String bookId, int bookmarkPage, List<String> highlights) {
        ReadingProgress progress = getProgress(username, bookId);
        progress.update(bookmarkPage, highlights);
        readingProgressRepository.save(progress);
        return progress;
    }

    public ReadingProgress addReadingSeconds(String username, String bookId, int seconds) {
        ReadingProgress progress = getProgress(username, bookId);
        progress.addReadingSeconds(seconds);
        readingProgressRepository.save(progress);
        return progress;
    }

    public ReadingProgress addReadingMinutes(String username, String bookId, int minutes) {
        return addReadingSeconds(username, bookId, Math.max(0, minutes) * 60);
    }
}
