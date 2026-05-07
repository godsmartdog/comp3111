package Library.Persistence;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class LibraryDatabaseService {
    public static final Path DEFAULT_DB_PATH = Path.of("data", "library-db.ser");

    private LibraryDatabaseService() {}

    public static Optional<LibraryDatabase> load(Path path) {
        if (!exists(path)) {
            return Optional.empty();
        }

        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(path))) {
            Object value = input.readObject();
            if (value instanceof LibraryDatabase database) {
                return Optional.of(database);
            }
            System.err.println("Warning: persistent library database has unexpected type: " + path);
        } catch (IOException | ClassNotFoundException | RuntimeException e) {
            System.err.println("Warning: failed to load persistent library database from " + path + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    public static void save(Path path, LibraryDatabase database) {
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(tempPath))) {
                output.writeObject(database);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write persistent library database to " + tempPath, e);
        }

        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallbackFailure) {
                throw new IllegalStateException("Failed to save persistent library database to " + path, fallbackFailure);
            }
        }
    }

    public static boolean exists(Path path) {
        return path != null && Files.exists(path);
    }
}