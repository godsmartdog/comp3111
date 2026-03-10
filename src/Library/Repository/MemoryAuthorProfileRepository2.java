package Library.Repository;

import Library.Model.AuthorProfile2;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MemoryAuthorProfileRepository2 implements AuthorProfileRepository2 {
    private final Map<String, AuthorProfile2> map = new HashMap<>();

    @Override
    public void save(AuthorProfile2 profile) {
        map.put(profile.getUsername(), profile);
    }

    @Override
    public Optional<AuthorProfile2> findByUsername(String username) {
        return Optional.ofNullable(map.get(username));
    }
}