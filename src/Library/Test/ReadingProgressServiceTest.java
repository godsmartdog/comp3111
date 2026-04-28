package Library.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import Library.Model.ReadingProgress;
import Library.Repository.MemoryReadingProgressRepository;
import Library.Service.ReadingProgressService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReadingProgressServiceTest {
	private ReadingProgressService service;

	@BeforeEach
	void setUp() {
		service = new ReadingProgressService(new MemoryReadingProgressRepository());
	}

	@Test
	void getProgressShouldCreateDefaultProgress() {
		ReadingProgress progress = service.getProgress("user1", "book1");

		assertEquals("user1", progress.getUsername());
		assertEquals("book1", progress.getBookId());
		assertEquals(1, progress.getBookmarkPage());
		assertTrue(service.findProgress("user1", "book1").isPresent());
	}

	@Test
	void updateProgressShouldSaveBookmarkAndHighlights() {
		ReadingProgress updated = service.updateProgress("user2", "book2", 12, List.of("h1", "h2"));

		assertEquals(12, updated.getBookmarkPage());
		assertEquals(List.of("h1", "h2"), updated.getHighlights());
	}

	@Test
	void addReadingMinutesShouldConvertToSeconds() {
		ReadingProgress updated = service.addReadingMinutes("user3", "book3", 3);

		assertEquals(180, updated.getTotalReadingSeconds());
	}
}
