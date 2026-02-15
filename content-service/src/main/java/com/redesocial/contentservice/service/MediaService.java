package com.redesocial.contentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    
    private final S3Client s3Client;
    
    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    
    @Value("${content.media.image.max-size-mb}")
    private int maxImageSizeMb;
    
    @Value("${content.media.video.max-size-mb}")
    private int maxVideoSizeMb;
    
    @Value("${content.media.image.thumbnail-width}")
    private int thumbnailWidth;
    
    @Value("${content.media.image.thumbnail-height}")
    private int thumbnailHeight;
    
    @Value("${content.media.image.allowed-formats}")
    private String allowedImageFormats;
    
    @Value("${content.media.video.allowed-formats}")
    private String allowedVideoFormats;
    
    private static final Set<String> VALID_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VALID_VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    
    public Map<String, String> uploadImageWithThumbnail(MultipartFile file, String userId) throws IOException {
        validateImageFile(file);
        
        String imageUrl = uploadFile(file, userId, "images");
        String thumbnailUrl = generateAndUploadThumbnail(file, userId);
        
        Map<String, String> result = new HashMap<>();
        result.put("imageUrl", imageUrl);
        result.put("thumbnailUrl", thumbnailUrl);
        
        log.info("Uploaded image and thumbnail for user {}: image={}, thumbnail={}", userId, imageUrl, thumbnailUrl);
        return result;
    }
    
    public Map<String, String> uploadVideoWithResolutions(MultipartFile file, String userId) throws IOException {
        validateVideoFile(file);
        
        // Upload original video
        String originalUrl = uploadFile(file, userId, "videos");
        
        // For now, we'll simulate video processing by uploading the original
        // In production, this would use FFmpeg or a video processing service
        Map<String, String> result = new HashMap<>();
        result.put("originalUrl", originalUrl);
        result.put("480p", originalUrl); // Simulated - would be processed version
        result.put("720p", originalUrl); // Simulated - would be processed version
        result.put("1080p", originalUrl); // Simulated - would be processed version
        
        log.info("Uploaded video for user {}: {}", userId, originalUrl);
        log.info("Video processing simulated - in production would generate 480p, 720p, 1080p versions");
        return result;
    }
    
    private String generateAndUploadThumbnail(MultipartFile file, String userId) throws IOException {
        ByteArrayOutputStream thumbnailOutputStream = new ByteArrayOutputStream();
        
        Thumbnails.of(file.getInputStream())
                .size(thumbnailWidth, thumbnailHeight)
                .outputFormat("jpg")
                .toOutputStream(thumbnailOutputStream);
        
        byte[] thumbnailBytes = thumbnailOutputStream.toByteArray();
        String fileName = UUID.randomUUID().toString() + "_thumb.jpg";
        String key = String.format("media/thumbnails/%s/%s", userId, fileName);
        
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("image/jpeg")
                .build();
        
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(thumbnailBytes));
        
        String url = String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        log.info("Generated and uploaded thumbnail: {}", url);
        
        return url;
    }
    
    public String uploadImage(MultipartFile file, String userId) throws IOException {
        validateImageFile(file);
        return uploadFile(file, userId, "images");
    }
    
    public String uploadVideo(MultipartFile file, String userId) throws IOException {
        validateVideoFile(file);
        return uploadFile(file, userId, "videos");
    }
    
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("INVALID_FILE: File is empty");
        }
        
        long maxSizeBytes = maxImageSizeMb * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("FILE_TOO_LARGE: Image exceeds " + maxImageSizeMb + "MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !VALID_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("INVALID_FILE_FORMAT: File must be JPEG, PNG, or WebP");
        }
    }
    
    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("INVALID_FILE: File is empty");
        }
        
        long maxSizeBytes = maxVideoSizeMb * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException("FILE_TOO_LARGE: Video exceeds " + maxVideoSizeMb + "MB");
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !VALID_VIDEO_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("INVALID_FILE_FORMAT: File must be MP4 or WebM");
        }
    }
    
    private String uploadFile(MultipartFile file, String userId, String folder) throws IOException {
        String fileName = UUID.randomUUID().toString();
        String key = String.format("media/%s/%s/%s", folder, userId, fileName);
        
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .build();
        
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        
        String url = String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        log.info("Uploaded file to S3: {}", url);
        
        return url;
    }
}
