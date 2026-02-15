package com.redesocial.contentservice.service;

import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for MediaService
 * Feature: rede-social-brasileira
 */
class MediaServiceProperties {
    
    private S3Client s3Client;
    private MediaService mediaService;
    
    @BeforeEach
    void setUp() {
        s3Client = Mockito.mock(S3Client.class);
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
    
    /**
     * Property 7: Upload de imagem válida cria thumbnail
     * **Validates: Requirements 2.2**
     * 
     * Para qualquer imagem válida (JPEG, PNG, WebP) menor que 10MB, o upload deve 
     * criar thumbnail e retornar URLs de imagem e thumbnail
     */
    @Property(tries = 100)
    void validImageUploadCreatesThumbnail(
            @ForAll("validImageFile") MultipartFile imageFile,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) throws IOException {
        // Act
        Map<String, String> result = mediaService.uploadImageWithThumbnail(imageFile, userId);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKeys("imageUrl", "thumbnailUrl");
        assertThat(result.get("imageUrl")).isNotNull().isNotEmpty();
        assertThat(result.get("thumbnailUrl")).isNotNull().isNotEmpty();
        assertThat(result.get("imageUrl")).contains("test-bucket");
        assertThat(result.get("thumbnailUrl")).contains("test-bucket");
        assertThat(result.get("thumbnailUrl")).contains("thumbnails");
        
        // Verify S3 was called twice (once for image, once for thumbnail)
        verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Reset mock for next iteration
        Mockito.clearInvocations(s3Client);
    }
    
    /**
     * Property 8: Upload de vídeo válido processa múltiplas resoluções
     * **Validates: Requirements 2.3**
     * 
     * Para qualquer vídeo válido (MP4, WebM) menor que 100MB, o upload deve processar 
     * e disponibilizar em resoluções 480p, 720p e 1080p
     */
    @Property(tries = 100)
    void validVideoUploadProcessesMultipleResolutions(
            @ForAll("validVideoFile") MultipartFile videoFile,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) throws IOException {
        // Act
        Map<String, String> result = mediaService.uploadVideoWithResolutions(videoFile, userId);
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result).containsKeys("originalUrl", "480p", "720p", "1080p");
        assertThat(result.get("originalUrl")).isNotNull().isNotEmpty();
        assertThat(result.get("480p")).isNotNull().isNotEmpty();
        assertThat(result.get("720p")).isNotNull().isNotEmpty();
        assertThat(result.get("1080p")).isNotNull().isNotEmpty();
        assertThat(result.get("originalUrl")).contains("test-bucket");
        assertThat(result.get("originalUrl")).contains("videos");
        
        // Verify S3 was called at least once for original upload
        verify(s3Client, atLeastOnce()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Reset mock for next iteration
        Mockito.clearInvocations(s3Client);
    }
    
    /**
     * Property 9: Arquivo excedendo tamanho máximo é rejeitado
     * **Validates: Requirements 2.4**
     * 
     * Para qualquer arquivo que excede limites (>10MB para imagem, >100MB para vídeo), 
     * o upload deve ser rejeitado com mensagem de erro específica
     */
    @Property(tries = 100)
    void oversizedImageIsRejected(
            @ForAll("oversizedImageFile") MultipartFile oversizedImage,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Act & Assert
        assertThatThrownBy(() -> mediaService.uploadImageWithThumbnail(oversizedImage, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FILE_TOO_LARGE")
                .hasMessageContaining("10MB");
        
        // Verify S3 was never called
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Reset mock for next iteration
        Mockito.clearInvocations(s3Client);
    }
    
    /**
     * Property 9 (continued): Arquivo excedendo tamanho máximo é rejeitado - Videos
     * **Validates: Requirements 2.4**
     * 
     * Para qualquer vídeo que excede 100MB, o upload deve ser rejeitado com 
     * mensagem de erro específica
     */
    @Property(tries = 100)
    void oversizedVideoIsRejected(
            @ForAll("oversizedVideoFile") MultipartFile oversizedVideo,
            @ForAll @AlphaChars @StringLength(min = 5, max = 36) String userId
    ) {
        // Act & Assert
        assertThatThrownBy(() -> mediaService.uploadVideoWithResolutions(oversizedVideo, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FILE_TOO_LARGE")
                .hasMessageContaining("100MB");
        
        // Verify S3 was never called
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        
        // Reset mock for next iteration
        Mockito.clearInvocations(s3Client);
    }
    
    // ==================== Arbitraries (Generators) ====================
    
    /**
     * Generates valid image files (JPEG, PNG, WebP) under 10MB
     */
    @Provide
    Arbitrary<MultipartFile> validImageFile() {
        return Combinators.combine(
                Arbitraries.of("image/jpeg", "image/png", "image/webp"),
                Arbitraries.of("test.jpg", "test.png", "test.webp"),
                Arbitraries.integers().between(1024, 10 * 1024 * 1024 - 1) // 1KB to just under 10MB
        ).as((contentType, filename, size) -> {
            byte[] content = createValidImageBytes(size);
            return new MockMultipartFile("file", filename, contentType, content);
        });
    }
    
    /**
     * Generates valid video files (MP4, WebM) under 100MB
     */
    @Provide
    Arbitrary<MultipartFile> validVideoFile() {
        return Combinators.combine(
                Arbitraries.of("video/mp4", "video/webm"),
                Arbitraries.of("test.mp4", "test.webm"),
                Arbitraries.integers().between(1024, 100 * 1024 * 1024 - 1) // 1KB to just under 100MB
        ).as((contentType, filename, size) -> {
            byte[] content = new byte[size];
            // Fill with some fake video data
            for (int i = 0; i < Math.min(size, 1000); i++) {
                content[i] = (byte) (i % 256);
            }
            return new MockMultipartFile("file", filename, contentType, content);
        });
    }
    
    /**
     * Generates oversized image files (over 10MB)
     */
    @Provide
    Arbitrary<MultipartFile> oversizedImageFile() {
        return Combinators.combine(
                Arbitraries.of("image/jpeg", "image/png", "image/webp"),
                Arbitraries.of("large.jpg", "large.png", "large.webp"),
                Arbitraries.integers().between(10 * 1024 * 1024 + 1, 15 * 1024 * 1024) // Over 10MB, up to 15MB
        ).as((contentType, filename, size) -> {
            byte[] content = new byte[size];
            return new MockMultipartFile("file", filename, contentType, content);
        });
    }
    
    /**
     * Generates oversized video files (over 100MB)
     */
    @Provide
    Arbitrary<MultipartFile> oversizedVideoFile() {
        return Combinators.combine(
                Arbitraries.of("video/mp4", "video/webm"),
                Arbitraries.of("large.mp4", "large.webm"),
                Arbitraries.integers().between(100 * 1024 * 1024 + 1, 110 * 1024 * 1024) // Over 100MB, up to 110MB
        ).as((contentType, filename, size) -> {
            byte[] content = new byte[size];
            return new MockMultipartFile("file", filename, contentType, content);
        });
    }
    
    /**
     * Creates a valid minimal PNG image with the specified size
     * For sizes larger than the minimal PNG, pads with zeros
     */
    private byte[] createValidImageBytes(int targetSize) {
        // Minimal 1x1 PNG image (67 bytes)
        byte[] minimalPng = new byte[]{
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
        
        if (targetSize <= minimalPng.length) {
            return minimalPng;
        }
        
        // Pad with zeros to reach target size
        byte[] result = new byte[targetSize];
        System.arraycopy(minimalPng, 0, result, 0, minimalPng.length);
        return result;
    }
}
