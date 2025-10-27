package com.celebstash.backend.controller;

import com.celebstash.backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload and download APIs")
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/upload/product-image")
    @Operation(summary = "Upload product image", description = "Upload a single product image")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestParam("file") MultipartFile file) {
        
        log.info("Uploading product image: {}", file.getOriginalFilename());
        String imageUrl = fileStorageService.storeProductImage(file);
        
        Map<String, String> response = new HashMap<>();
        response.put("imageUrl", imageUrl);
        response.put("message", "Image uploaded successfully");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/product-video")
    @Operation(summary = "Upload product video", description = "Upload a single product video")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, String>> uploadProductVideo(
            @RequestParam("file") MultipartFile file) {
        
        log.info("Uploading product video: {}", file.getOriginalFilename());
        String videoUrl = fileStorageService.storeProductVideo(file);
        
        Map<String, String> response = new HashMap<>();
        response.put("videoUrl", videoUrl);
        response.put("message", "Video uploaded successfully");
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload/post-photos")
    @Operation(summary = "Upload post photos", description = "Upload 3-5 photos for a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> uploadPostPhotos(
            @RequestParam("files") MultipartFile[] files) {
        
        log.info("Uploading {} post photos", files.length);
        String[] photoUrls = fileStorageService.storeProductPhotos(files);
        
        Map<String, Object> response = new HashMap<>();
        response.put("photoUrls", photoUrls);
        response.put("count", photoUrls.length);
        response.put("message", "Photos uploaded successfully");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/**")
    @Operation(summary = "Download file", description = "Download a file by its path")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam(required = false) String download,
            jakarta.servlet.http.HttpServletRequest request) {
        
        try {
            // Get the full path from the request
            String fullPath = request.getRequestURI().replace("/api/files/", "");
            
            // Load file as Resource
            Path filePath = Paths.get("uploads").resolve(fullPath).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                log.error("File not found or not readable: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            // Determine content type
            String contentType;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException ex) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
            
            // Build response
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType));
            
            // If download parameter is present, force download
            if (download != null) {
                responseBuilder.header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + resource.getFilename() + "\"");
            }
            
            return responseBuilder.body(resource);
            
        } catch (Exception ex) {
            log.error("Error downloading file", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
