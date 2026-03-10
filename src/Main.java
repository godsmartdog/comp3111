import Library.Model.*;
import Library.Repository.*;
import Library.Service.*;
import Library.Ui.*;

import java.time.LocalDate;

public class Main {
    public static void main(String[] args) {
        UserRepository userRepo = new MemoryUserRepository();
        BookRepository bookRepo = new MemoryBookRepository();
        BorrowRepository borrowRepo = new MemoryBorrowRepository();

        // seed approved books (from teammate author/librarian flow eventually)
        Book b1 = new Book("Clean Code", "Robert C. Martin", "A handbook of agile software craftsmanship.");
        b1.approve(LocalDate.now().minusDays(2));
        bookRepo.save(b1);

        Book b2 = new Book("Design Patterns", "GoF", "Classic OO design patterns and examples.");
        b2.approve(LocalDate.now().minusDays(1));
        bookRepo.save(b2);

        AuthService authService = new AuthService(userRepo);
        BookService bookService = new BookService(bookRepo);
        BorrowService borrowService = new BorrowService(bookRepo, borrowRepo);

        ConsoleUI consoleUi = new ConsoleUI(authService, bookService, borrowService);
        consoleUi.start();
    }
}