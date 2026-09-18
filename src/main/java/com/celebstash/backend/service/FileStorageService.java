package com.celebstash.backend.service;

<<<<<<< HEAD
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
=======
import com.celebstash.backend.model.StoredFile;
import com.celebstash.backend.repository.StoredFileRepository;
>>>>>>> d8b0c20a20f1fe235107c9e84bc1b64c7958d5ad
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
<<<<<<< HEAD
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
=======
import java.time.LocalDateTime;
import java.util.*;
>>>>>>> d8b0c20a20f1fe235107c9e84bc1b64c7958d5ad

@Slf4j
@Service
public class FileStorageService {

    private final StoredFileRepository storedFileRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final Path fileStorageLocation;

<<<<<<< HEAD
    /**
     * Cloudinary client, or {@code null} when no credentials are configured.
     *
     * <p>Uploads written to the container filesystem do not survive a redeploy on a PaaS, so a
     * deployed instance must push media to durable storage instead. When Cloudinary is configured
     * uploads go there and the public CDN URL is returned; otherwise the original local-disk
     * behaviour is kept so local development works with no extra setup.
     */
    private final Cloudinary cloudinary;
    private final String cloudinaryFolder;

    public FileStorageService(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${cloudinary.url:}") String cloudinaryUrl,
            @Value("${cloudinary.folder:celebstash}") String cloudinaryFolder) {
        this.fileStorageLocation = Paths.get(uploadDir)
                .toAbsolutePath().normalize();
        this.cloudinaryFolder = cloudinaryFolder;

        Cloudinary client = null;
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            try {
                client = new Cloudinary(cloudinaryUrl);
                client.config.secure = true;
                log.info("Cloudinary storage enabled (folder '{}'); uploads will be stored remotely.", cloudinaryFolder);
            } catch (Exception ex) {
                log.error("CLOUDINARY_URL is set but could not be parsed; falling back to local disk storage. Cause: {}",
                        ex.getMessage());
            }
        } else {
            log.warn("Cloudinary is not configured — uploads go to the local filesystem '{}'. "
                    + "On a platform with an ephemeral filesystem these files are lost on redeploy.", this.fileStorageLocation);
        }
        this.cloudinary = client;
=======
    public FileStorageService(
            StoredFileRepository storedFileRepository,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
            @Value("${file.upload-dir:uploads}") String uploadDir) {
        this.storedFileRepository = storedFileRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
>>>>>>> d8b0c20a20f1fe235107c9e84bc1b64c7958d5ad

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            log.warn("Could not create local upload directory: {}", ex.getMessage());
        }
    }

    /** True when uploads are persisted to Cloudinary rather than the local filesystem. */
    public boolean isRemoteStorageEnabled() {
        return cloudinary != null;
    }

    /**
     * Store a file and return the URL clients should use to fetch it.
     *
     * <p>Returns an absolute Cloudinary CDN URL when remote storage is enabled, otherwise the
     * local {@code /api/files/{name}} URL, so callers never need to know where a file landed.
     */
    public String storeFileAndGetPublicUrl(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        if (cloudinary != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "folder", cloudinaryFolder,
                                // "auto" lets Cloudinary accept images and video through one call.
                                "resource_type", "auto",
                                "public_id", UUID.randomUUID().toString(),
                                "overwrite", true));

                Object secureUrl = result.get("secure_url");
                if (secureUrl == null) {
                    secureUrl = result.get("url");
                }
                if (secureUrl == null) {
                    throw new IllegalStateException("Cloudinary response contained no URL");
                }
                return withAutoFormat(String.valueOf(secureUrl), String.valueOf(result.get("resource_type")));
            } catch (Exception ex) {
                // Surface the failure rather than silently writing somewhere that will be wiped.
                throw new RuntimeException("Could not upload file to Cloudinary: " + ex.getMessage(), ex);
            }
        }

        return toLocalPublicUrl(storeFile(file));
    }

    /** Store several files, returning their public URLs in the same order. */
    public List<String> storeFilesAndGetPublicUrls(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                urls.add(storeFileAndGetPublicUrl(file));
            }
        }
        return urls;
    }

    /**
     * Adds Cloudinary's {@code f_auto,q_auto} delivery transformation to an upload URL.
     *
     * <p>Phones upload in their native formats — iOS sends HEIC, which no mainstream browser can
     * render, so the stored image loads on the phone but shows as a broken image in the dashboard.
     * {@code f_auto} makes Cloudinary transcode per request (WebP for Chrome, JPEG elsewhere) and
     * {@code q_auto} trims the payload, without re-encoding anything at upload time.
     *
     * <p>Only applied to image and video assets; {@code raw} files have no transformation pipeline.
     */
    private String withAutoFormat(String url, String resourceType) {
        if (url == null || !("image".equals(resourceType) || "video".equals(resourceType))) {
            return url;
        }
        final String marker = "/upload/";
        int idx = url.indexOf(marker);
        if (idx < 0) {
            return url;
        }
        int insertAt = idx + marker.length();
        return url.substring(0, insertAt) + "f_auto,q_auto/" + url.substring(insertAt);
    }

    /**
     * Builds the absolute URL for a locally stored file, falling back to a root-relative path when
     * there is no request bound to the thread (the clients resolve a leading "/" themselves).
     */
    private String toLocalPublicUrl(String fileName) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/files/")
                    .path(fileName)
                    .toUriString();
        } catch (IllegalStateException ex) {
            return "/api/files/" + fileName;
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