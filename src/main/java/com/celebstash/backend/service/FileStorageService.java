package com.celebstash.backend.service;

import com.celebstash.backend.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private final Path fileStorageLocation;
    private final String baseUrl;

    public FileStorageService(
            @Value("${file.upload-dir:uploads}") String uploadDir,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;

        try {
            Files.createDirectories(this.fileStorageLocation);
            log.info("File storage location created at: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            throw new AppException("Could not create the directory where the uploaded files will be stored", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Store a file and return its URL
     */
    public String storeFile(MultipartFile file, String subdirectory) {
        // Validate file
        if (file.isEmpty()) {
            throw new AppException("Failed to store empty file", HttpStatus.BAD_REQUEST);
        }

        // Get original filename
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        
        // Check for invalid characters
        if (originalFilename.contains("..")) {
            throw new AppException("Filename contains invalid path sequence: " + originalFilename, HttpStatus.BAD_REQUEST);
        }

        try {
            // Generate unique filename
            String fileExtension = getFileExtension(originalFilename);
            String newFilename = UUID.randomUUID().toString() + "." + fileExtension;
            
            // Create subdirectory if provided
            Path targetLocation;
            if (subdirectory != null && !subdirectory.isEmpty()) {
                Path subdir = this.fileStorageLocation.resolve(subdirectory);
                Files.createDirectories(subdir);
                targetLocation = subdir.resolve(newFilename);
            } else {
                targetLocation = this.fileStorageLocation.resolve(newFilename);
            }

            // Copy file to target location
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            // Return URL
            String relativePath = subdirectory != null && !subdirectory.isEmpty() 
                ? subdirectory + "/" + newFilename 
                : newFilename;
            
            String fileUrl = baseUrl + "/api/files/" + relativePath;
            log.info("File stored successfully: {}", fileUrl);
            
            return fileUrl;
            
        } catch (IOException ex) {
            log.error("Failed to store file: {}", originalFilename, ex);
            throw new AppException("Failed to store file: " + originalFilename, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Store a product image
     */
    public String storeProductImage(MultipartFile file) {
        validateImageFile(file);
        return storeFile(file, "products");
    }

    /**
     * Store a product video
     */
    public String storeProductVideo(MultipartFile file) {
        validateVideoFile(file);
        return storeFile(file, "videos");
    }

    /**
     * Store multiple product photos
     */
    public String[] storeProductPhotos(MultipartFile[] files) {
        if (files == null || files.length < 3 || files.length > 5) {
            throw new AppException("Between 3 and 5 photos are required", HttpStatus.BAD_REQUEST);
        }

        String[] urls = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            validateImageFile(files[i]);
            urls[i] = storeFile(files[i], "posts");
        }
        
        return urls;
    }

    /**
     * Validate image file
     */
    private void validateImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException("Only image files are allowed", HttpStatus.BAD_REQUEST);
        }

        // Check file size (max 5MB for images)
        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new AppException("Image file size must not exceed 5MB", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Validate video file
     */
    private void validateVideoFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new AppException("Only video files are allowed", HttpStatus.BAD_REQUEST);
        }

        // Check file size (max 50MB for videos)
        long maxSize = 50 * 1024 * 1024; // 50MB
        if (file.getSize() > maxSize) {
            throw new AppException("Video file size must not exceed 50MB", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Get file extension from filename
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new AppException("Invalid filename", HttpStatus.BAD_REQUEST);
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throw new AppException("File must have an extension", HttpStatus.BAD_REQUEST);
        }
        
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * Delete a file
     */
    public void deleteFile(String fileUrl) {
        try {
            // Extract relative path from URL
            String relativePath = fileUrl.replace(baseUrl + "/api/files/", "");
            Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
            
            Files.deleteIfExists(filePath);
            log.info("File deleted successfully: {}", fileUrl);
            
        } catch (IOException ex) {
            log.error("Failed to delete file: {}", fileUrl, ex);
            // Don't throw exception, just log the error
        }
    }
}
