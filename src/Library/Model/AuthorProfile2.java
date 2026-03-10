package Library.Model; //imported to AuthorProfileRepository2.java for interface

public class AuthorProfile2 { // Profile for Author with parameters: {username, bio}

    // Member variables - final keyword for immutability
    private final String username;
    private final String bio;

    // Initialize the member variables or update data
    public AuthorProfile2(String username, String bio) {
        this.username = username;
        this.bio = bio;
    }

    // Accessor
    public String getUsername() { return username; }
    public String getBio() { return bio; }
}
