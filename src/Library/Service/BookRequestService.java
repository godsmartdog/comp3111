package Library.Service;

import Library.Exception.NotFoundException;
import Library.Exception.ValidationException;
import Library.Model.Book;
import Library.Model.BookRequest2;
import Library.Model.BookRequestStatus;
import Library.Repository.BookRepository;
import Library.Repository.BookRequestRepository2;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BookRequestService {
    private final BookRequestRepository2 requestRepository;
    private final BookRepository bookRepository;

    public BookRequestService(BookRequestRepository2 requestRepository, BookRepository bookRepository) {
        this.requestRepository = requestRepository;
        this.bookRepository = bookRepository;
    }

    public BookRequest2 submitRequest(String requesterUsername,
                                      String requesterFullName,
                                      String title,
                                      String authorName,
                                      String genres,
                                      String reason) {
        String normalizedTitle = requireNonBlank(title, "Title");
        String normalizedAuthorName = requireNonBlank(authorName, "Author");
        String normalizedReason = requireNonBlank(reason, "Reason for Request");
        List<String> normalizedGenres = parseGenres(genres);
        if (normalizedGenres.isEmpty()) {
            throw new ValidationException("Genre cannot be empty.");
        }

        BookRequest2 request = new BookRequest2(
                normalizedTitle,
                requesterUsername,
                requesterFullName,
                normalizedAuthorName,
                normalizedGenres,
                normalizedReason
        );
        requestRepository.save(request);
        return request;
    }

    public List<BookRequest2> listRequests() {
        return requestRepository.findAll().stream()
                .sorted(Comparator.comparing(BookRequest2::getRequestedDate).reversed()
                        .thenComparing(BookRequest2::getId))
                .collect(Collectors.toList());
    }

    public List<BookRequest2> listRequestsByRequester(String username) {
        return requestRepository.findByRequesterUsername(username).stream()
                .sorted(Comparator.comparing(BookRequest2::getRequestedDate).reversed()
                        .thenComparing(BookRequest2::getId))
                .collect(Collectors.toList());
    }

    public List<BookRequest2> listRequestsByStatus(BookRequestStatus status) {
        return requestRepository.findByStatus(status).stream()
                .sorted(Comparator.comparing(BookRequest2::getRequestedDate).reversed()
                        .thenComparing(BookRequest2::getId))
                .collect(Collectors.toList());
    }

    public BookRequest2 getRequestByIdForReview(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new ValidationException("Request ID is required.");
        }
        return requestRepository.findById(requestId.trim())
                .orElseThrow(() -> new NotFoundException("Book request not found."));
    }

    public BookRequest2 approveRequest(String requestId, String comment) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.PENDING) {
            throw new ValidationException("Only pending requests can be approved.");
        }
        request.approve(comment == null ? "Approved" : comment.trim());
        requestRepository.save(request);
        return request;
    }

    public BookRequest2 rejectRequest(String requestId, String comment, String rejectionReason) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.PENDING) {
            throw new ValidationException("Only pending requests can be rejected.");
        }
        request.reject(comment == null ? "" : comment.trim(), rejectionReason == null ? "" : rejectionReason.trim());
        requestRepository.save(request);
        return request;
    }

    public BookRequest2 uploadRequest(String requestId, String comment) {
        return uploadRequest(requestId, comment, null, "", "");
    }

    public BookRequest2 uploadRequest(String requestId,
                                      String comment,
                                      String summary,
                                      String filePath,
                                      String contentType) {
        BookRequest2 request = getRequestByIdForReview(requestId);
        if (request.getStatus() != BookRequestStatus.APPROVED) {
            throw new ValidationException("Only approved requests can be uploaded.");
        }

        String resolvedSummary = (summary == null || summary.isBlank())
                ? request.getReason()
                : summary.trim();

        Book book = new Book(
                request.getTitle(),
                "",
                request.getAuthorName(),
                request.getGenres(),
                resolvedSummary
        );
        if (filePath != null && !filePath.isBlank()) {
            book.setFileMetadata(filePath.trim(), contentType == null ? "" : contentType.trim());
        }
        book.approve(LocalDate.now());
        bookRepository.save(book);

        request.markUploaded(comment == null ? "Uploaded" : comment.trim(), book.getId());
        requestRepository.save(request);
        return request;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new ValidationException(fieldName + " cannot be empty.");
        }
        return value.trim();
    }

    private static List<String> parseGenres(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> genres = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                genres.add(trimmed);
            }
        }
        return genres;
    }
}