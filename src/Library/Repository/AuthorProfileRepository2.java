package Library.Repository;

import Library.Model.AuthorProfile2;

import java.util.Optional;

public interface AuthorProfileRepository2 {
    void save(AuthorProfile2 profile);
    Optional<AuthorProfile2> findByUsername(String username);
}
