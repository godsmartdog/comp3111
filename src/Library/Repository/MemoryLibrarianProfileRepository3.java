package Library.Repository;

import Library.Model.LibrarianProfile3;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MemoryLibrarianProfileRepository3 implements LibrarianProfileRepository3 {
    private final Map<String, LibrarianProfile3> map = new HashMap<>();

    @Override
    public void save(LibrarianProfile3 profile) {
        map.put(profile.getUsername(), profile);
    }

    @Override
    public Optional<LibrarianProfile3> findByUsername(String username) {
        return Optional.ofNullable(map.get(username));
    }
}