package Library.Model;
//imported to AuthorProfileRepository2.java for interface
public class AuthorProfile2 {
    private final String username;
    private final String bio;

    public AuthorProfile2(String username, String bio) {
        this.username = username;
        this.bio = bio;
    }

    public String getUsername() { return username; }
    public String getBio() { return bio; }
}
