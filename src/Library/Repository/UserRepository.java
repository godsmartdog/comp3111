package Library.Repository;

import Library.Model.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    void save(User user);
    boolean existsByUsername(String username);
    List<User> findAll();
}

