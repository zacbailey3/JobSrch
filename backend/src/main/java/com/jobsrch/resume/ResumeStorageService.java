package com.jobsrch.resume;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.jobsrch.config.StorageProperties;

/**
 * Stores resume bytes outside the database while keeping only safe metadata in
 * the relational model.
 *
 * <p>The client filename is preserved for display only. Files are written using
 * generated names beneath one normalized root, preventing path traversal and
 * accidental overwrites.</p>
 */
@Service
public class ResumeStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 200;
    private static final long MAX_DOCX_ENTRY_SIZE = 20L * 1024 * 1024;
    private static final long MAX_DOCX_UNCOMPRESSED_SIZE = 30L * 1024 * 1024;

    private final Path root;
    private final ResumeMalwareScanner malwareScanner;

    public ResumeStorageService(
            StorageProperties properties,
            ResumeMalwareScanner malwareScanner) {
        this.root = properties.resumeDirectory().toAbsolutePath().normalize();
        this.malwareScanner = malwareScanner;
    }

    public StoredResume store(MultipartFile file) {
        validate(file);
        String originalFilename = safeOriginalFilename(file);
        String extension = extensionOf(originalFilename);
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path destination = root.resolve(storedFilename).normalize();
        if (!destination.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filename");
        }

        try {
            Files.createDirectories(root);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredResume(
                    originalFilename,
                    storedFilename,
                    normalizedContentType(file, extension),
                    file.getSize());
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not store resume", exception);
        }
    }

    public void delete(String storedFilename) {
        Path target = pathFor(storedFilename);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not delete resume", exception);
        }
    }

    /**
     * Resolves an internal generated filename for trusted backend readers.
     * The normalized-root check remains here so analysis code cannot bypass the
     * same filesystem boundary used during upload.
     */
    public Path pathFor(String storedFilename) {
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stored filename");
        }
        return target;
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Resume must be 10 MB or smaller");
        }
        String extension = extensionOf(safeOriginalFilename(file));
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Resume must be a PDF or DOCX");
        }
        validateFileSignature(file, extension);
        malwareScanner.scan(file);
    }

    /**
     * An extension and browser-supplied MIME type are not evidence of a file's
     * real format. PDFs must have the PDF signature; DOCX uploads must be ZIP
     * containers containing the core Word document entry.
     */
    private void validateFileSignature(MultipartFile file, String extension) {
        try {
            if ("pdf".equals(extension)) {
                byte[] signature = file.getInputStream().readNBytes(5);
                if (!java.util.Arrays.equals(signature, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw invalidFileContents();
                }
                return;
            }

            boolean hasContentTypes = false;
            boolean hasDocument = false;
            try (InputStream input = file.getInputStream();
                    ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                int inspected = 0;
                long totalUncompressed = 0;
                byte[] buffer = new byte[8 * 1024];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++inspected > MAX_DOCX_ENTRIES || unsafeZipEntry(entry.getName())) {
                        throw invalidFileContents();
                    }
                    hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                    hasDocument |= "word/document.xml".equals(entry.getName());

                    long entryUncompressed = 0;
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        entryUncompressed += read;
                        totalUncompressed += read;
                        if (entryUncompressed > MAX_DOCX_ENTRY_SIZE
                                || totalUncompressed > MAX_DOCX_UNCOMPRESSED_SIZE) {
                            throw invalidFileContents();
                        }
                    }
                }
            }
            if (!hasContentTypes || !hasDocument) {
                throw invalidFileContents();
            }
        } catch (IOException exception) {
            throw invalidFileContents();
        }
    }

    private boolean unsafeZipEntry(String name) {
        return name == null
                || name.startsWith("/")
                || name.startsWith("\\")
                || name.contains("../")
                || name.contains("..\\");
    }

    private ResponseStatusException invalidFileContents() {
        return new ResponseStatusException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "File contents do not match a valid PDF or DOCX resume");
    }

    private String safeOriginalFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume filename is missing");
        }
        String normalized = Path.of(filename).getFileName().toString().trim();
        if (normalized.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resume filename is too long");
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        int separator = filename.lastIndexOf('.');
        return separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(MultipartFile file, String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        };
    }

    public record StoredResume(
            String originalFilename,
            String storedFilename,
            String contentType,
            long sizeBytes) {
    }
}
