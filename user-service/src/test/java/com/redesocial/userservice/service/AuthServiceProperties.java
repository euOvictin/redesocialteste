package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.AuthResponse;
import com.redesocial.userservice.dto.RegisterRequest;
import com.redesocial.userservice.exception.EmailAlreadyExistsException;
import com.redesocial.userservice.model.User;
import com.redesocial.userservice.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for user registration
 * Feature: rede-social-brasileira
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceProperties {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    /**
     * Property 1: Registro de usuário válido cria conta
     * Para qualquer conjunto válido de dados de registro (email único, senha forte, nome),
     * criar uma conta deve retornar um token de autenticação válido
     * **Validates: Requirements 1.1**
     */
    @Property(tries = 100)
    @Label("Property 1: Valid user registration creates account with auth token")
    void validUserRegistrationCreatesAccountWithAuthToken(
            @ForAll @Email String email,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String name
    ) {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(name)
                .build();

        // Act
        AuthResponse response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUserId()).isNotBlank();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);

        // Verify user was created in database
        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getName()).isEqualTo(name);
        assertThat(savedUser.getFollowersCount()).isEqualTo(0);
        assertThat(savedUser.getFollowingCount()).isEqualTo(0);
    }

    /**
     * Property 2: Email duplicado rejeita registro
     * Para qualquer email já registrado no sistema, tentar criar nova conta com esse email
     * deve resultar em rejeição com mensagem de erro descritiva
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 100)
    @Label("Property 2: Duplicate email rejects registration")
    void duplicateEmailRejectsRegistration(
            @ForAll @Email String email,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password1,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password2,
            @ForAll @StringLength(min = 2, max = 100) String name1,
            @ForAll @StringLength(min = 2, max = 100) String name2
    ) {
        // Arrange - Register first user
        RegisterRequest firstRequest = RegisterRequest.builder()
                .email(email)
                .password(password1)
                .name(name1)
                .build();
        authService.register(firstRequest);

        // Act & Assert - Try to register second user with same email
        RegisterRequest secondRequest = RegisterRequest.builder()
                .email(email)
                .password(password2)
                .name(name2)
                .build();

        assertThatThrownBy(() -> authService.register(secondRequest))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already registered");
    }

    /**
     * Property 4: Senhas são armazenadas com hash bcrypt
     * Para qualquer senha fornecida durante registro, o sistema nunca deve armazenar
     * a senha em texto plano e deve usar bcrypt com mínimo 10 rounds
     * **Validates: Requirements 1.5**
     */
    @Property(tries = 100)
    @Label("Property 4: Passwords are stored with bcrypt hash")
    void passwordsAreStoredWithBcryptHash(
            @ForAll @Email String email,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String name
    ) {
        // Arrange
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(name)
                .build();

        // Act
        authService.register(request);

        // Assert
        User savedUser = userRepository.findByEmail(email).orElseThrow();
        
        // Password should not be stored in plain text
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(password);
        
        // Password hash should start with $2a$ or $2b$ (bcrypt identifier)
        assertThat(savedUser.getPasswordHash()).matches("^\\$2[ab]\\$.*");
        
        // Verify bcrypt can validate the password
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
        
        // Verify wrong password doesn't match
        assertThat(passwordEncoder.matches(password + "wrong", savedUser.getPasswordHash())).isFalse();
    }

    // Custom email generator for jqwik
    @Provide
    Arbitrary<String> email() {
        Arbitrary<String> localPart = Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars("._-")
                .ofMinLength(1)
                .ofMaxLength(20);
        
        Arbitrary<String> domain = Arbitraries.strings()
                .alpha()
                .ofMinLength(3)
                .ofMaxLength(15);
        
        Arbitrary<String> tld = Arbitraries.of("com", "br", "net", "org", "io");
        
        return Combinators.combine(localPart, domain, tld)
                .as((local, dom, t) -> local + "@" + dom + "." + t);
    }
}
