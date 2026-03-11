package Library.Repository;
//class used
import Library.Model.LibrarianProfile3;
//way to store
import java.util.HashMap;
import java.util.Map;
//safe return
import java.util.Optional;

public class MemoryLibrarianProfileRepository3 implements LibrarianProfileRepository3 {
    private final Map<String, LibrarianProfile3> map = new HashMap<>();
    //we use username as key
    @Override
    public void save(LibrarianProfile3 profile) {
        map.put(profile.getUsername(), profile);
    }
    //we use key to search
    @Override
    public Optional<LibrarianProfile3> findByUsername(String username) {
        return Optional.ofNullable(map.get(username));
    }
}
