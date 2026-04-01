package Library.Service;

import Library.Exception.ValidationException;
import Library.Security.SecurityConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Service class to handle file-related operations such as validating book submission files and providing preview details for submitted files, ensuring that the files meet the defined criteria for format and size before processing them further.
public class FileService {
    private static final int DEFAULT_PREVIEW_MAX_LINES = 40;
    private static final int DEFAULT_PREVIEW_MAX_CHARS = 4000;

    public void validateSubmissionFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new ValidationException("Book file path is required.");
        }

        // Validate file extension against allowed formats defined in SecurityConfig, ensuring that only supported file types are accepted for book submissions.
        String lower = filePath.toLowerCase(Locale.ROOT);
        boolean allowed = SecurityConfig.ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new ValidationException("Unsupported file format. Allowed: " + SecurityConfig.ALLOWED_EXTENSIONS);
        }

        // Validate that the file exists and is a regular file, throwing a ValidationException if the file does not exist or is not a valid file, which helps prevent issues during file processing later on.
        Path path = normalizeSafePath(filePath);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ValidationException("Uploaded file does not exist: " + filePath);
        }

        try {
            long size = Files.size(path);
            if (size > SecurityConfig.MAX_FILE_SIZE_BYTES) {
                throw new ValidationException("File too large. Max size is " + SecurityConfig.MAX_FILE_SIZE_BYTES + " bytes.");
            }
        } catch (IOException e) {
            throw new ValidationException("Cannot read file size: " + e.getMessage());
        }
    }

    // Method to get preview details of a submitted file, providing information such as file path, size, and a content preview for text files, while also handling potential IO exceptions that may occur during file access.
    public String getPreviewDetails(String filePath) {
        validateSubmissionFile(filePath);
        Path path = normalizeSafePath(filePath);

        try {
            long bytes = Files.size(path);
            StringBuilder sb = new StringBuilder();
            sb.append("File path: ").append(path.toAbsolutePath()).append(System.lineSeparator());
            sb.append("File size: ").append(bytes).append(" bytes").append(System.lineSeparator());

            String lower = filePath.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".txt") || lower.endsWith(".md")) {
                sb.append("--- File Preview (first up to 20 lines) ---").append(System.lineSeparator());
                Files.lines(path).limit(20).forEach(line -> sb.append(line).append(System.lineSeparator()));
                sb.append("--- End Preview ---").append(System.lineSeparator());
            } else {
                sb.append("Console mode cannot render PDF/DOC/DOCX content directly.").append(System.lineSeparator());
                sb.append("Use the file path above to open/download full content.").append(System.lineSeparator());
            }
            return sb.toString();
        } catch (IOException e) {
            throw new ValidationException("Cannot preview file: " + e.getMessage());
        }
    }

    public TextPreview readSafeTextPreview(String filePath) {
        validateSubmissionFile(filePath);
        Path path = normalizeSafePath(filePath);

        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".txt") || lower.endsWith(".md"))) {
            throw new ValidationException("Only text files (.txt, .md) are supported for reading preview.");
        }

        try {
            long size = Files.size(path);
            List<String> lines = new ArrayList<>();
            try (var stream = Files.lines(path)) {
                stream.limit(DEFAULT_PREVIEW_MAX_LINES).forEach(lines::add);
            }
            String preview = String.join(System.lineSeparator(), lines);
            if (preview.length() > DEFAULT_PREVIEW_MAX_CHARS) {
                preview = preview.substring(0, DEFAULT_PREVIEW_MAX_CHARS) + "...";
            }
            return new TextPreview(path.toAbsolutePath().toString(), size, preview);
        } catch (IOException e) {
            throw new ValidationException("Cannot read file preview: " + e.getMessage());
        }
    }

    private Path normalizeSafePath(String filePath) {
        Path original = Path.of(filePath);
        for (Path segment : original) {
            if ("..".equals(segment.toString())) {
                throw new ValidationException("Unsafe file path is not allowed.");
            }
        }
        return original.toAbsolutePath().normalize();
    }

    public record TextPreview(String absolutePath, long sizeBytes, String previewText) {
    }
}
