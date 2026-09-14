package com.celebstash.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path fileStorageLocation;

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

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
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
     * Store a single file
     * @param file the file to store
     * @return the filename of the stored file
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file.");
        }

        String rawFileName = file.getOriginalFilename();
        String originalFileName = (rawFileName != null && !rawFileName.isBlank())
                ? StringUtils.cleanPath(rawFileName)
                : "upload_" + System.currentTimeMillis() + ".jpg";
        
        try {
            // Check if the file's name contains invalid characters
            if (originalFileName.contains("..")) {
                throw new RuntimeException("Sorry! Filename contains invalid path sequence " + originalFileName);
            }

            // Generate a unique file name to prevent duplicates
            String fileExtension = "";
            if (originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            } else {
                String contentType = file.getContentType();
                if (contentType != null) {
                    if (contentType.contains("png")) fileExtension = ".png";
                    else if (contentType.contains("gif")) fileExtension = ".gif";
                    else if (contentType.contains("mp4")) fileExtension = ".mp4";
                    else fileExtension = ".jpg";
                } else {
                    fileExtension = ".jpg";
                }
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;

            // Copy file to the target location (replacing existing file with the same name)
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            return fileName;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + originalFileName + ". Please try again!", ex);
        }
    }

    /**
     * Store multiple files
     * @param files the files to store
     * @return a list of filenames of the stored files
     */
    public List<String> storeFiles(List<MultipartFile> files) {
        List<String> fileNames = new ArrayList<>();
        for (MultipartFile file : files) {
            fileNames.add(storeFile(file));
        }
        return fileNames;
    }

    /**
     * Load a file as a Resource
     * @param fileName the name of the file to load
     * @return the file as a Resource
     */
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }
}