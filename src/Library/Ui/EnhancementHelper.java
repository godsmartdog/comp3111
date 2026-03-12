// [Task 1 nice-to-have]
package Library.Ui;

import Library.Model.Book;
import Library.Model.BorrowRecord;
import javafx.scene.paint.Color;

import java.time.LocalDate;

public class EnhancementHelper {

    // [Task 1 nice-to-have]
    public static void printAvailability(Book b) {
        // Console fallback of red/black
        String colorTag = b.isAvailable() ? "[BLACK/AVAILABLE]" : "[RED/UNAVAILABLE]";
        System.out.printf("%s %s%n", colorTag, b.getTitle());
    }

    // [Task 1 nice-to-have]
    public static void quickReadSummary(Book b) {
        String summary = b.getSummary();
        if (summary == null || summary.isBlank()) {
            System.out.println("Summary: N/A");
            return;
        }
        int threshold = 120;
        if (summary.length() <= threshold) {
            System.out.println("Summary: " + summary);
        } else {
            System.out.println("Summary is long. Quick-read preview:");
            System.out.println(summary.substring(0, threshold) + "...");
            System.out.println("(Use full view to read complete summary)");
        }
    }

    // [Task 1 nice-to-have]
    public static boolean confirmBorrow(String bookTitle, int durationDays) {
        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(durationDays);
        System.out.println("Confirm Borrow:");
        System.out.println("- Book: " + bookTitle);
        System.out.println("- Duration: " + durationDays + " days");
        System.out.println("- Due Date: " + due);
        System.out.println("- Warning: Late return may incur penalties.");
        return true; // replace with actual user yes/no in UI
    }

    // [Task 1 nice-to-have]
    public static Color getAvailabilityColor(Book b) {
        return b.isAvailable() ? Color.BLACK : Color.RED;
    }

    // [Task 2 nice-to-have]
    public static String buildBorrowConfirmation(String bookTitle, int durationDays) {
        LocalDate now = LocalDate.now();
        LocalDate due = now.plusDays(durationDays);
        return "Book: " + bookTitle
                + "\nDuration: " + durationDays + " days"
                + "\nDue Date: " + due
                + "\nWarning: Late return may incur penalties.";
    }

    // [Task 2 nice-to-have]
    public static void printBorrowResult(BorrowRecord record) {
        System.out.printf("Borrowed. Start=%s Due=%s%n", record.getBorrowDate(), record.getDueDate());
    }
}
