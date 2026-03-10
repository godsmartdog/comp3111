//This file will be used in Library.service./LibrarianService3.java

//function( save(), findByUsername()) defined in Memoryxxxx -> just go see this
// Import the system library used for extension handling
package Library.Repository;
//import class from LibrarianProfile3
import Library.Model.LibrarianProfile3;
//safe return
import java.util.Optional;

public interface LibrarianProfileRepository3 {
    void save(LibrarianProfile3 profile);
    Optional<LibrarianProfile3> findByUsername(String username);
}
