package com.celebstash.backend.controller;

import com.celebstash.backend.service.FileStorageService;
import com.celebstash.backend.util.UrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final UrlSigner urlSigner;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = fileStorageService.storeFile(file);

            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/files/")
                    .path(fileName)
                    .toUriString();

            return ResponseEntity.ok(fileDownloadUri);
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("File upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/uploadMultiple")
    public ResponseEntity<?> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            List<String> fileDownloadUrls = Arrays.stream(files)
                    .map(file -> {
                        String fileName = fileStorageService.storeFile(file);
                        return ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/api/files/")
                                .path(fileName)
                                .toUriString();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(fileDownloadUrls);
        } catch (Exception e) {
            log.error("Failed to upload multiple files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Multiple files upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request) {
        String cleanFileName = cleanFileName(fileName);
        Optional<com.celebstash.backend.model.StoredFile> sfOpt = fileStorageService.getStoredFile(cleanFileName);
        if (sfOpt.isPresent()) {
            com.celebstash.backend.model.StoredFile sf = sfOpt.get();
            String contentType = sf.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = java.net.URLConnection.guessContentTypeFromName(cleanFileName);
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(sf.getData()) {
                @Override
                public String getFilename() {
                    return sf.getFileName();
                }
            };
            long length = sf.getSize() != null ? sf.getSize() : (sf.getData() != null ? sf.getData().length : 0);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sf.getFileName() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);
        }

        Resource resource = fileStorageService.loadFileAsResource(cleanFileName);
        String contentType = java.net.URLConnection.guessContentTypeFromName(cleanFileName);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        try {
            long length = resource.contentLength();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
    }

    @GetMapping("/stream/{fileName:.+}")
    public ResponseEntity<Resource> streamFile(
            @PathVariable String fileName,
            @RequestParam("token") String token,
            @RequestParam("expires") Long expires,
            HttpServletRequest request) {

        String path = "/api/files/stream/" + fileName;
        if (!urlSigner.verifySignature(path, expires, token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String cleanFileName = cleanFileName(fileName);
        Optional<com.celebstash.backend.model.StoredFile> sfOpt = fileStorageService.getStoredFile(cleanFileName);
        if (sfOpt.isPresent()) {
            com.celebstash.backend.model.StoredFile sf = sfOpt.get();
            String contentType = sf.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = java.net.URLConnection.guessContentTypeFromName(cleanFileName);
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(sf.getData()) {
                @Override
                public String getFilename() {
                    return sf.getFileName();
                }
            };
            long length = sf.getSize() != null ? sf.getSize() : (sf.getData() != null ? sf.getData().length : 0);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sf.getFileName() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);
        }

        Resource resource = fileStorageService.loadFileAsResource(cleanFileName);
        String contentType = java.net.URLConnection.guessContentTypeFromName(cleanFileName);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        try {
            long length = resource.contentLength();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        }
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