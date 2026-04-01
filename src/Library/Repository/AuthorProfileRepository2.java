//This file will be used in Library.service.AuthorService2 
//function( save(), findByUsername()) defined in Memoryxxxx -> just go see this
// Import the system library used for extension handling
package Library.Repository;
//import the class of AuthorProfile2
import Library.Model.AuthorProfile2;
//for safe return
import java.util.Optional;

public interface AuthorProfileRepository2 {
    //save the profile
    void save(AuthorProfile2 profile);
    //username is para in profile, AuthorProfile2 object may be returned
    Optional<AuthorProfile2> findByUsername(String username);
}
