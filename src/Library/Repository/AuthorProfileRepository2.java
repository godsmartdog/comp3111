//This file will be used in Library.service.AuthorService2 
//function defined in Memoryxxxx
// Import the system library used for extension handling
package Library.Repository;
//import the class of AuthorProfile2
import Library.Model.AuthorProfile2;

import java.util.Optional;

public interface AuthorProfileRepository2 {
    //save the profile
    void save(AuthorProfile2 profile);
    //username is para in profile, AuthorProfile2 object should be returned
    Optional<AuthorProfile2> findByUsername(String username);
}
