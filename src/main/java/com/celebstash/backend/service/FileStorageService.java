package com.celebstash.backend.service;

import com.celebstash.backend.model.StoredFile;
import com.celebstash.backend.repository.StoredFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class FileStorageService {

    private final StoredFileRepository storedFileRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final Path fileStorageLocation;

    public FileStorageService(
            StoredFileRepository storedFileRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            @Value("${file.upload-dir:uploads}") String uploadDir) {
        this.storedFileRepository = storedFileRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            log.warn("Could not create local upload directory: {}", ex.getMessage());
        }
    }

    /**
     * Ensure database table exists, and auto-migrate any existing files from local uploads/ directory into PostgreSQL database.
     */
    @PostConstruct
    public void migrateExistingFilesToDatabase() {
        // Guarantee stored_files table exists in PostgreSQL database
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS stored_files (
                    id BIGSERIAL PRIMARY KEY,
                    file_name VARCHAR(255) NOT NULL UNIQUE,
                    original_file_name VARCHAR(255),
                    content_type VARCHAR(100),
                    size BIGINT,
                    data BYTEA,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                CREATE INDEX IF NOT EXISTS idx_stored_files_name ON stored_files(file_name);
            """);
            log.info("Verified stored_files table in PostgreSQL database.");
        } catch (Exception ex) {
            log.warn("Could not verify/create stored_files table (may already exist): {}", ex.getMessage());
        }

        try {
            File dir = this.fileStorageLocation.toFile();
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    int migratedCount = 0;
                    for (File f : files) {
                        if (f.isFile() && !storedFileRepository.existsByFileName(f.getName())) {
                            try {
                                byte[] data = Files.readAllBytes(f.toPath());
                                String contentType = Files.probeContentType(f.toPath());
                                if (contentType == null) {
                                    String ext = f.getName().toLowerCase();
                                    if (ext.endsWith(".png")) contentType = "image/png";
                                    else if (ext.endsWith(".gif")) contentType = "image/gif";
                                    else if (ext.endsWith(".mp3")) contentType = "audio/mpeg";
                                    else if (ext.endsWith(".mp4")) contentType = "video/mp4";
                                    else contentType = "image/jpeg";
                                }

                                StoredFile storedFile = StoredFile.builder()
                                        .fileName(f.getName())
                                        .originalFileName(f.getName())
                                        .contentType(contentType)
                                        .size(f.length())
                                        .data(data)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                storedFileRepository.save(storedFile);
                                migratedCount++;
                            } catch (Exception err) {
                                log.warn("Failed to migrate file {}: {}", f.getName(), err.getMessage());
                            }
                        }
                    }
                    if (migratedCount > 0) {
                        log.info("Migrated {} existing uploaded files into the PostgreSQL database.", migratedCount);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Auto-migration of local files to database skipped: {}", e.getMessage());
        }
    }

    /**
     * Store a file directly in PostgreSQL database
     */
    @Transactional
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        String rawFileName = file.getOriginalFilename();
        String originalFileName = (rawFileName != null && !rawFileName.isBlank())
                ? StringUtils.cleanPath(rawFileName)
                : "upload_" + System.currentTimeMillis() + ".jpg";

        if (originalFileName.contains("..")) {
            throw new RuntimeException("Sorry! Filename contains invalid path sequence " + originalFileName);
        }

        String fileExtension = "";
        if (originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        } else {
            String contentType = file.getContentType();
            if (contentType != null) {
                if (contentType.contains("png")) fileExtension = ".png";
                else if (contentType.contains("gif")) fileExtension = ".gif";
                else if (contentType.contains("mp4")) fileExtension = ".mp4";
                else if (contentType.contains("mp3")) fileExtension = ".mp3";
                else fileExtension = ".jpg";
            } else {
                fileExtension = ".jpg";
            }
        }

        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            byte[] data = file.getBytes();
            String contentType = file.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            // 1. Save directly into the PostgreSQL database!
            StoredFile storedFile = StoredFile.builder()
                    .fileName(fileName)
                    .originalFileName(originalFileName)
                    .contentType(contentType)
                    .size(file.getSize())
                    .data(data)
                    .createdAt(LocalDateTime.now())
                    .build();

            storedFileRepository.save(storedFile);
            log.info("File {} stored directly in PostgreSQL database ({} bytes)", fileName, data.length);

            // 2. Also keep a local cache copy on disk
            try {
                Path targetLocation = this.fileStorageLocation.resolve(fileName);
                Files.write(targetLocation, data);
            } catch (Exception err) {
                log.debug("Local disk backup skipped: {}", err.getMessage());
            }

            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        }
    }

    public List<String> storeFiles(List<MultipartFile> files) {
        List<String> fileNames = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    fileNames.add(storeFile(file));
                }
            }
        }
        return fileNames;
    }

    @Transactional(readOnly = true)
    public Optional<StoredFile> getStoredFile(String fileName) {
        String clean = cleanFileName(fileName);
        return storedFileRepository.findByFileName(clean);
    }

    @Transactional(readOnly = true)
    public Resource loadFileAsResource(String fileName) {
        String clean = cleanFileName(fileName);

        // 1. Check PostgreSQL database first
        Optional<StoredFile> fileOpt = storedFileRepository.findByFileName(clean);
        if (fileOpt.isPresent()) {
            StoredFile sf = fileOpt.get();
            return new ByteArrayResource(sf.getData()) {
                @Override
                public String getFilename() {
                    return sf.getFileName();
                }
            };
        }

        // 2. Fallback to local filesystem if not found in database
        try {
            Path filePath = this.fileStorageLocation.resolve(clean).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            }
        } catch (Exception ignored) {}

        throw new RuntimeException("File not found " + clean);
    }

    private String cleanFileName(String fileName) {
        if (fileName == null) return "";
        if (fileName.contains("/api/files/")) {
            fileName = fileName.substring(fileName.lastIndexOf("/api/files/") + 11);
        } else if (fileName.contains("/")) {
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        }
        return fileName.trim();
    }
}