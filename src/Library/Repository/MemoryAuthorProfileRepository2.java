//This file will be used in Library.service.AuthorService2 

// Import the system library used for extension handling
package Library.Repository;
//import class
import Library.Model.AuthorProfile2;
import java.io.Serializable;
//stores pairs of information
import java.util.HashMap;
import java.util.Map;
//safe return
import java.util.Optional;

public class MemoryAuthorProfileRepository2 implements AuthorProfileRepository2, Serializable {
    private static final long serialVersionUID = 1L;

    //make a map first
    private final Map<String, AuthorProfile2> map = new HashMap<>();

    //Takes an AuthorProfile2 object
    //Gets its username with profile.getUsername()
    //Stores it in the map with username as the key and the whole profile as the value
    @Override
    public void save(AuthorProfile2 profile) {
        map.put(profile.getUsername(), profile);
    }

    //Tries to get an author from the map using the username: map.get(username)
    //Wraps the result in Optional.ofNullable() which handles null safely
    //How it works:
    //If author FOUND: map.get("john") returns the author → wrapped in Optional.of(author)
    //If author NOT found: map.get("john") returns null → wrapped in Optional.empty()
    @Override
    public Optional<AuthorProfile2> findByUsername(String username) {
        return Optional.ofNullable(map.get(username));
    }
}
