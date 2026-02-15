package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.AuthResponse;
import com.redesocial.userservice.dto.LoginRequest;
import com.redesocial.userservice.dto.RegisterRequest;
import com.redesocial.userservice.exception.EmailAlreadyExistsException;
import com.redesocial.userservice.exception.InvalidCredentialsException;
import com.redesocial.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_WithValidData_ReturnsAuthResponse() {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .password("password123")
                .name("Test User")
                .build();

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUserId()).isNotBlank();
    }

    @Test
    void register_WithDuplicateEmail_ThrowsEmailAlreadyExistsException() {
        // Arrange
        RegisterRequest request1 = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("password123")
                .name("User One")
                .build();
        authService.register(request1);

        RegisterRequest request2 = RegisterRequest.builder()
                .email("duplicate@example.com")
                .password("differentpass")
                .name("User Two")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request2))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already registered");
    }

    @Test
    void login_WithValidCredentials_ReturnsAuthResponse() {
        // Arrange
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("login@example.com")
                .password("password123")
                .name("Login User")
                .build();
        authService.register(registerRequest);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("login@example.com")
                .password("password123")
                .build();

        // Act
        AuthResponse response = authService.login(loginRequest);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
    }

    @Test
    void login_WithInvalidEmail_ThrowsInvalidCredentialsException() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("password123")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_WithInvalidPassword_ThrowsInvalidCredentialsException() {
        // Arrange
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("wrongpass@example.com")
                .password("correctpassword")
                .name("Test User")
                .build();
        authService.register(registerRequest);

        LoginRequest loginRequest = LoginRequest.builder()
                .email("wrongpass@example.com")
                .password("wrongpassword")
                .build();

        // Act & Assert
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
