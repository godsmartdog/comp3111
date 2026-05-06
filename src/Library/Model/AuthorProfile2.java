package Library.Model; //imported to AuthorProfileRepository2.java for interface

import java.io.Serializable;

// Profile for Author with parameters: {username, bio}
public class AuthorProfile2 implements Serializable {
    private static final long serialVersionUID = 1L;

    // Member variables - final keyword for immutability
    private final String username;
    private final String bio;

    // Constructor
    public AuthorProfile2(String username, String bio) {
        this.username = username;
        this.bio = bio;
    }

    // Accessor
    public String getUsername() { return username; }
    public String getBio() { return bio; }
}
