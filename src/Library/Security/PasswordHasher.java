package Library.Security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PasswordHasher {
    // Parameters chosen to balance security and performance for a library system.
    //salt is some random value added into password to avoid situation of same password but diff acc
    private static final int SALT_BYTES = 16;
    private static final int KEY_LENGTH_BITS = 256;
    //number of hashing time-> safety
    private static final int ITERATIONS = 120_000;
    //Industry standard for password hashing
    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";
    //version label of hashing method, for future upgrade, doesnt matter for now
    private static final String PREFIX = "pbkdf2$";

    // For backward compatibility with legacy unsalted SHA-256 hashes.
    public static String sha256(String rawPassword) {
        return hashPassword(rawPassword);
    }

    // Hashes the password using PBKDF2 with a random salt and returns a string containing all necessary info.
    public static String hashPassword(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];//create empty container
        new SecureRandom().nextBytes(salt);//fill in the container with random value
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);//hashing, function defined under

        return PREFIX 
                + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    // Verifies a raw password against the stored hash, supporting both PBKDF2 and legacy SHA-256 formats.
    public static boolean matches(String rawPassword, String storedHash) {
        if (storedHash != null && storedHash.startsWith(PREFIX)) {//see if hash using PBKDF2WithHmacSHA256 this standard
            String[] parts = storedHash.split("\\$");// $ is added in hashing,{ prefix,iteration,salt and hash}, must be 4 part
            if (parts.length != 4) {
                return false;
            }
            //just get back the data
            int iterations;
            try {
                iterations = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return false;
            }

            byte[] salt;
            byte[] expected;
            try {
                salt = Base64.getDecoder().decode(parts[2]);
                expected = Base64.getDecoder().decode(parts[3]);
            } catch (IllegalArgumentException e) {
                return false;
            }
            //check match
            byte[] actual = pbkdf2(rawPassword.toCharArray(), salt, iterations, expected.length * 8);//hashing, function defined under
            return MessageDigest.isEqual(actual, expected);
        }

        // Backward compatibility for legacy unsalted SHA-256 hashes.
        //check if pasword using previous version
        //which mean useless, but just leave it here, it doesnt affect anyway.
        //function defined under
        return legacySha256(rawPassword).equals(storedHash);
    }

    // Internal method to perform PBKDF2 hashing.
    //function we use in hashing
    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLengthBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);//put all para tgt
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGO);//state method we use
            return skf.generateSecret(spec).getEncoded();//actually hashing
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("PBKDF2 unavailable", e);
        }
    }

    // Internal method for legacy SHA-256 hashing (not recommended for new passwords).
    //probably uselss, way to hash
    private static String legacySha256(String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
