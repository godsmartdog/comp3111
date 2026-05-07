package Library.Repository;
//class
import Library.Model.User;
import java.io.Serializable;
import java.util.*;

public class MemoryUserRepository implements UserRepository, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, User> users = new HashMap<>();
    //search by key
    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(users.get(username));
    }
    //username is the key
    @Override
    public void save(User user) {
        users.put(user.getUsername(), user);
    }
    //return true if exists
    @Override
    public boolean existsByUsername(String username) {
        return users.containsKey(username);
    }
    //everything shown
    @Override
    public List<User> findAll() {
        return new ArrayList<>(users.values());
    }
}
