package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.RegisterRequest;
import com.redesocial.userservice.dto.UpdateProfileRequest;
import com.redesocial.userservice.dto.UserResponse;
import com.redesocial.userservice.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for user profile management
 * Feature: rede-social-brasileira
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceProperties {

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    /**
     * Property 5: Atualização de perfil persiste alterações
     * Para qualquer atualização válida de perfil (nome, bio, foto),
     * as alterações devem ser persistidas e refletidas em consultas subsequentes
     * **Validates: Requirements 1.6**
     */
    @Property(tries = 100)
    @Label("Property 5: Profile update persists changes")
    void profileUpdatePersistsChanges(
            @ForAll @Email String email,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String initialName,
            @ForAll @StringLength(min = 2, max = 100) String updatedName,
            @ForAll @StringLength(min = 1, max = 500) String bio,
            @ForAll @StringLength(min = 10, max = 200) String profilePictureUrl,
            @ForAll boolean isPrivate
    ) {
        // Arrange - Create user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(initialName)
                .build();
        String userId = authService.register(registerRequest).getUserId();

        // Act - Update profile
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .name(updatedName)
                .bio(bio)
                .profilePictureUrl(profilePictureUrl)
                .isPrivate(isPrivate)
                .build();
        userService.updateProfile(userId, updateRequest);

        // Assert - Verify changes persisted
        UserResponse response = userService.getUserById(userId);
        assertThat(response.getName()).isEqualTo(updatedName);
        assertThat(response.getBio()).isEqualTo(bio);
        assertThat(response.getProfilePictureUrl()).isEqualTo(profilePictureUrl);
        assertThat(response.getIsPrivate()).isEqualTo(isPrivate);
        
        // Verify changes are reflected in subsequent queries
        UserResponse secondQuery = userService.getUserById(userId);
        assertThat(secondQuery.getName()).isEqualTo(updatedName);
        assertThat(secondQuery.getBio()).isEqualTo(bio);
        assertThat(secondQuery.getProfilePictureUrl()).isEqualTo(profilePictureUrl);
        assertThat(secondQuery.getIsPrivate()).isEqualTo(isPrivate);
    }

    /**
     * Property: Partial profile update only changes specified fields
     */
    @Property(tries = 100)
    @Label("Partial profile update only changes specified fields")
    void partialProfileUpdateOnlyChangesSpecifiedFields(
            @ForAll @Email String email,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String initialName,
            @ForAll @StringLength(min = 2, max = 100) String updatedName
    ) {
        // Arrange - Create user with initial data
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(initialName)
                .build();
        String userId = authService.register(registerRequest).getUserId();

        // Act - Update only name
        UpdateProfileRequest updateRequest = UpdateProfileRequest.builder()
                .name(updatedName)
                .build();
        userService.updateProfile(userId, updateRequest);

        // Assert - Only name changed, other fields remain null/default
        UserResponse response = userService.getUserById(userId);
        assertThat(response.getName()).isEqualTo(updatedName);
        assertThat(response.getEmail()).isEqualTo(email);
        // Bio should remain null since we didn't update it
        assertThat(response.getBio()).isNull();
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
