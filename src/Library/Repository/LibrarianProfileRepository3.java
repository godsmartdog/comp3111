package Library.Repository;

import Library.Model.LibrarianProfile3;

import java.util.Optional;

public interface LibrarianProfileRepository3 {
    void save(LibrarianProfile3 profile);
    Optional<LibrarianProfile3> findByUsername(String username);
}
