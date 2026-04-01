package Library.Repository;
//class user
import Library.Model.User;
import java.util.List;
import java.util.Optional;
//just go check Memoryxxx this just interface
public interface UserRepository {
    Optional<User> findByUsername(String username);
    void save(User user);
    boolean existsByUsername(String username);
    List<User> findAll();
}

