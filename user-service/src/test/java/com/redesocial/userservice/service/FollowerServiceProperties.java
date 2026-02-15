package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.RegisterRequest;
import com.redesocial.userservice.dto.UserResponse;
import com.redesocial.userservice.repository.FollowerRepository;
import com.redesocial.userservice.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for follower relationships
 * Feature: rede-social-brasileira
 */
@SpringBootTest
@ActiveProfiles("test")
class FollowerServiceProperties {

    @Autowired
    private FollowerService followerService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowerRepository followerRepository;

    @BeforeEach
    void setUp() {
        followerRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * Property 21: Seguir usuário cria relacionamento
     * Para quaisquer dois usuários distintos A e B, A seguir B deve criar
     * relacionamento e incrementar contadores
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Label("Property 21: Following user creates relationship and increments counters")
    void followingUserCreatesRelationshipAndIncrementsCounters(
            @ForAll @Email String email1,
            @ForAll @Email String email2,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password1,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password2,
            @ForAll @StringLength(min = 2, max = 100) String name1,
            @ForAll @StringLength(min = 2, max = 100) String name2
    ) {
        Assume.that(!email1.equals(email2)); // Ensure different users

        // Arrange - Create two users
        String userId1 = createUser(email1, password1, name1);
        String userId2 = createUser(email2, password2, name2);

        UserResponse user1Before = userService.getUserById(userId1);
        UserResponse user2Before = userService.getUserById(userId2);

        // Act - User1 follows User2
        followerService.followUser(userId1, userId2);

        // Assert - Relationship created and counters incremented
        UserResponse user1After = userService.getUserById(userId1);
        UserResponse user2After = userService.getUserById(userId2);

        assertThat(user1After.getFollowingCount()).isEqualTo(user1Before.getFollowingCount() + 1);
        assertThat(user2After.getFollowersCount()).isEqualTo(user2Before.getFollowersCount() + 1);

        // Verify relationship exists in database
        assertThat(followerRepository.existsByFollowerIdAndFollowingId(userId1, userId2)).isTrue();
    }

    /**
     * Property 22: Deixar de seguir remove relacionamento
     * Para qualquer relacionamento existente, deixar de seguir deve remover
     * o relacionamento e decrementar contadores corretamente
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    @Label("Property 22: Unfollowing removes relationship and decrements counters")
    void unfollowingRemovesRelationshipAndDecrementsCounters(
            @ForAll @Email String email1,
            @ForAll @Email String email2,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password1,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password2,
            @ForAll @StringLength(min = 2, max = 100) String name1,
            @ForAll @StringLength(min = 2, max = 100) String name2
    ) {
        Assume.that(!email1.equals(email2)); // Ensure different users

        // Arrange - Create two users and establish follow relationship
        String userId1 = createUser(email1, password1, name1);
        String userId2 = createUser(email2, password2, name2);
        followerService.followUser(userId1, userId2);

        UserResponse user1Before = userService.getUserById(userId1);
        UserResponse user2Before = userService.getUserById(userId2);

        // Act - User1 unfollows User2
        followerService.unfollowUser(userId1, userId2);

        // Assert - Relationship removed and counters decremented
        UserResponse user1After = userService.getUserById(userId1);
        UserResponse user2After = userService.getUserById(userId2);

        assertThat(user1After.getFollowingCount()).isEqualTo(user1Before.getFollowingCount() - 1);
        assertThat(user2After.getFollowersCount()).isEqualTo(user2Before.getFollowersCount() - 1);

        // Verify relationship no longer exists
        assertThat(followerRepository.existsByFollowerIdAndFollowingId(userId1, userId2)).isFalse();
    }

    /**
     * Property 23: Contadores refletem relacionamentos reais
     * Para qualquer usuário, os contadores de seguidores e seguindo devem sempre
     * corresponder ao número real de relacionamentos no banco
     * **Validates: Requirements 5.4**
     */
    @Property(tries = 50)
    @Label("Property 23: Counters reflect actual relationships")
    void countersReflectActualRelationships(
            @ForAll @Email String email1,
            @ForAll @Email String email2,
            @ForAll @Email String email3,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String name1,
            @ForAll @StringLength(min = 2, max = 100) String name2,
            @ForAll @StringLength(min = 2, max = 100) String name3
    ) {
        Assume.that(!email1.equals(email2) && !email1.equals(email3) && !email2.equals(email3));

        // Arrange - Create three users
        String userId1 = createUser(email1, password, name1);
        String userId2 = createUser(email2, password, name2);
        String userId3 = createUser(email3, password, name3);

        // Act - Create various relationships
        // User1 follows User2 and User3
        followerService.followUser(userId1, userId2);
        followerService.followUser(userId1, userId3);
        // User2 follows User1
        followerService.followUser(userId2, userId1);

        // Assert - Counters match actual relationships
        UserResponse user1 = userService.getUserById(userId1);
        UserResponse user2 = userService.getUserById(userId2);
        UserResponse user3 = userService.getUserById(userId3);

        // User1: following 2, followers 1
        assertThat(user1.getFollowingCount()).isEqualTo(2);
        assertThat(user1.getFollowersCount()).isEqualTo(1);
        assertThat(followerRepository.countByFollowerId(userId1)).isEqualTo(2);
        assertThat(followerRepository.countByFollowingId(userId1)).isEqualTo(1);

        // User2: following 1, followers 1
        assertThat(user2.getFollowingCount()).isEqualTo(1);
        assertThat(user2.getFollowersCount()).isEqualTo(1);
        assertThat(followerRepository.countByFollowerId(userId2)).isEqualTo(1);
        assertThat(followerRepository.countByFollowingId(userId2)).isEqualTo(1);

        // User3: following 0, followers 1
        assertThat(user3.getFollowingCount()).isEqualTo(0);
        assertThat(user3.getFollowersCount()).isEqualTo(1);
        assertThat(followerRepository.countByFollowerId(userId3)).isEqualTo(0);
        assertThat(followerRepository.countByFollowingId(userId3)).isEqualTo(1);
    }

    /**
     * Property 24: Lista de seguidores paginada com 50 por página
     * Para qualquer requisição de lista de seguidores, cada página deve conter
     * exatamente 50 usuários (ou menos na última página)
     * **Validates: Requirements 5.5**
     */
    @Property(tries = 20)
    @Label("Property 24: Followers list paginated with 50 per page")
    void followersListPaginatedWith50PerPage(
            @ForAll @Email String targetEmail,
            @ForAll @StringLength(min = 8, max = 50) @AlphaChars String password,
            @ForAll @StringLength(min = 2, max = 100) String targetName
    ) {
        // Arrange - Create target user
        String targetUserId = createUser(targetEmail, password, targetName);

        // Create 75 followers (to test pagination across 2 pages)
        for (int i = 0; i < 75; i++) {
            String followerEmail = "follower" + i + "@test.com";
            String followerId = createUser(followerEmail, password, "Follower " + i);
            followerService.followUser(followerId, targetUserId);
        }

        // Act & Assert - First page should have 50 followers
        List<UserResponse> page1 = followerService.getFollowers(targetUserId, 0, 50);
        assertThat(page1).hasSize(50);

        // Second page should have 25 followers (remaining)
        List<UserResponse> page2 = followerService.getFollowers(targetUserId, 1, 50);
        assertThat(page2).hasSize(25);

        // Third page should be empty
        List<UserResponse> page3 = followerService.getFollowers(targetUserId, 2, 50);
        assertThat(page3).isEmpty();
    }

    private String createUser(String email, String password, String name) {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(name)
                .build();
        return authService.register(request).getUserId();
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
