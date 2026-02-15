package com.redesocial.contentservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    
    @Mock
    private S3Client s3Client;
    
    private MediaService mediaService;
    
    @BeforeEach
    void setUp() {
        mediaService = new MediaService(s3Client);
        ReflectionTestUtils.setField(mediaService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(mediaService, "maxImageSizeMb", 10);
        ReflectionTestUtils.setField(mediaService, "maxVideoSizeMb", 100);
        ReflectionTestUtils.setField(mediaService, "thumbnailWidth", 300);
        ReflectionTestUtils.setField(mediaService, "thumbnailHeight", 300);
        ReflectionTestUtils.setField(mediaService, "allowedImageFormats", "jpeg,jpg,png,webp");
        ReflectionTestUtils.setField(mediaService, "allowedVideoFormats", "mp4,webm");
        
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }
    
    @Test
    void uploadImageWithThumbnail_ValidImage_Success() throws IOException {
        // Create a small test image (1x1 pixel PNG)
        byte[] imageBytes = createSmallPngImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                imageBytes
        );
        
        Map<String, String> result = mediaService.uploadImageWithThumbnail(file, "user123");
        
        assertNotNull(result);
        assertTrue(result.containsKey("imageUrl"));
        assertTrue(result.containsKey("thumbnailUrl"));
        assertTrue(result.get("imageUrl").contains("test-bucket"));
        assertTrue(result.get("thumbnailUrl").contains("test-bucket"));
        
        // Verify S3 was called twice (once for image, once for thumbnail)
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadImageWithThumbnail_ImageTooLarge_ThrowsException() {
        // Create a file larger than 10MB
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.png",
                "image/png",
                largeBytes
        );
        
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mediaService.uploadImageWithThumbnail(file, "user123")
        );
        
        assertTrue(exception.getMessage().contains("FILE_TOO_LARGE"));
        assertTrue(exception.getMessage().contains("10MB"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadImageWithThumbnail_InvalidFormat_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );
        
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mediaService.uploadImageWithThumbnail(file, "user123")
        );
        
        assertTrue(exception.getMessage().contains("INVALID_FILE_FORMAT"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadImageWithThumbnail_EmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.png",
                "image/png",
                new byte[0]
        );
        
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mediaService.uploadImageWithThumbnail(file, "user123")
        );
        
        assertTrue(exception.getMessage().contains("INVALID_FILE"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadVideoWithResolutions_ValidVideo_Success() throws IOException {
        byte[] videoBytes = "fake video content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.mp4",
                "video/mp4",
                videoBytes
        );
        
        Map<String, String> result = mediaService.uploadVideoWithResolutions(file, "user123");
        
        assertNotNull(result);
        assertTrue(result.containsKey("originalUrl"));
        assertTrue(result.containsKey("480p"));
        assertTrue(result.containsKey("720p"));
        assertTrue(result.containsKey("1080p"));
        
        // Verify S3 was called once for original upload
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadVideoWithResolutions_VideoTooLarge_ThrowsException() {
        // Create a file larger than 100MB
        byte[] largeBytes = new byte[101 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.mp4",
                "video/mp4",
                largeBytes
        );
        
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mediaService.uploadVideoWithResolutions(file, "user123")
        );
        
        assertTrue(exception.getMessage().contains("FILE_TOO_LARGE"));
        assertTrue(exception.getMessage().contains("100MB"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadVideoWithResolutions_InvalidFormat_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.avi",
                "video/avi",
                "not a valid format".getBytes()
        );
        
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> mediaService.uploadVideoWithResolutions(file, "user123")
        );
        
        assertTrue(exception.getMessage().contains("INVALID_FILE_FORMAT"));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadImage_ValidJpeg_Success() throws IOException {
        byte[] imageBytes = createSmallPngImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                imageBytes
        );
        
        String result = mediaService.uploadImage(file, "user123");
        
        assertNotNull(result);
        assertTrue(result.contains("test-bucket"));
        assertTrue(result.contains("images"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadImage_ValidWebP_Success() throws IOException {
        byte[] imageBytes = createSmallPngImage();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.webp",
                "image/webp",
                imageBytes
        );
        
        String result = mediaService.uploadImage(file, "user123");
        
        assertNotNull(result);
        assertTrue(result.contains("test-bucket"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    @Test
    void uploadVideo_ValidWebM_Success() throws IOException {
        byte[] videoBytes = "fake webm content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.webm",
                "video/webm",
                videoBytes
        );
        
        String result = mediaService.uploadVideo(file, "user123");
        
        assertNotNull(result);
        assertTrue(result.contains("test-bucket"));
        assertTrue(result.contains("videos"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
    
    // Helper method to create a minimal valid PNG image
    private byte[] createSmallPngImage() {
        // Minimal 1x1 PNG image
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54,
                0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00, 0x05,
                0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00,
                0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42,
                0x60, (byte) 0x82
        };
    }
}
