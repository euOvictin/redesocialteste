package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.RegisterRequest;
import com.redesocial.userservice.exception.CannotFollowSelfException;
import com.redesocial.userservice.exception.UserNotFoundException;
import com.redesocial.userservice.repository.FollowerRepository;
import com.redesocial.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for edge cases in follower service
 */
@SpringBootTest
@ActiveProfiles("test")
class FollowerServiceTest {

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

    @Test
    void followUser_WhenUserTriesToFollowSelf_ThrowsCannotFollowSelfException() {
        // Arrange
        String userId = createUser("self@example.com", "password123", "Self User");

        // Act & Assert
        assertThatThrownBy(() -> followerService.followUser(userId, userId))
                .isInstanceOf(CannotFollowSelfException.class)
                .hasMessageContaining("cannot follow themselves");
    }

    @Test
    void followUser_WhenFollowerDoesNotExist_ThrowsUserNotFoundException() {
        // Arrange
        String existingUserId = createUser("existing@example.com", "password123", "Existing User");
        String nonExistentUserId = "non-existent-id";

        // Act & Assert
        assertThatThrownBy(() -> followerService.followUser(nonExistentUserId, existingUserId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("Follower not found");
    }

    @Test
    void followUser_WhenUserToFollowDoesNotExist_ThrowsUserNotFoundException() {
        // Arrange
        String existingUserId = createUser("existing@example.com", "password123", "Existing User");
        String nonExistentUserId = "non-existent-id";

        // Act & Assert
        assertThatThrownBy(() -> followerService.followUser(existingUserId, nonExistentUserId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User to follow not found");
    }

    @Test
    void followUser_WhenAlreadyFollowing_IsIdempotent() {
        // Arrange
        String userId1 = createUser("user1@example.com", "password123", "User 1");
        String userId2 = createUser("user2@example.com", "password123", "User 2");

        // Act - Follow twice
        followerService.followUser(userId1, userId2);
        followerService.followUser(userId1, userId2);

        // Assert - Counters should only increment once
        assertThat(userService.getUserById(userId1).getFollowingCount()).isEqualTo(1);
        assertThat(userService.getUserById(userId2).getFollowersCount()).isEqualTo(1);
    }

    @Test
    void unfollowUser_WhenNotFollowing_IsIdempotent() {
        // Arrange
        String userId1 = createUser("user1@example.com", "password123", "User 1");
        String userId2 = createUser("user2@example.com", "password123", "User 2");

        // Act - Unfollow without following first
        followerService.unfollowUser(userId1, userId2);

        // Assert - Counters should remain at 0
        assertThat(userService.getUserById(userId1).getFollowingCount()).isEqualTo(0);
        assertThat(userService.getUserById(userId2).getFollowersCount()).isEqualTo(0);
    }

    @Test
    void unfollowUser_MultipleTimesDoesNotDecrementBelowZero() {
        // Arrange
        String userId1 = createUser("user1@example.com", "password123", "User 1");
        String userId2 = createUser("user2@example.com", "password123", "User 2");
        followerService.followUser(userId1, userId2);

        // Act - Unfollow multiple times
        followerService.unfollowUser(userId1, userId2);
        followerService.unfollowUser(userId1, userId2);
        followerService.unfollowUser(userId1, userId2);

        // Assert - Counters should not go below 0
        assertThat(userService.getUserById(userId1).getFollowingCount()).isEqualTo(0);
        assertThat(userService.getUserById(userId2).getFollowersCount()).isEqualTo(0);
    }

    @Test
    void getFollowers_WhenUserDoesNotExist_ThrowsUserNotFoundException() {
        // Arrange
        String nonExistentUserId = "non-existent-id";

        // Act & Assert
        assertThatThrownBy(() -> followerService.getFollowers(nonExistentUserId, 0, 50))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getFollowing_WhenUserDoesNotExist_ThrowsUserNotFoundException() {
        // Arrange
        String nonExistentUserId = "non-existent-id";

        // Act & Assert
        assertThatThrownBy(() -> followerService.getFollowing(nonExistentUserId, 0, 50))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getFollowers_WhenUserHasNoFollowers_ReturnsEmptyList() {
        // Arrange
        String userId = createUser("lonely@example.com", "password123", "Lonely User");

        // Act
        var followers = followerService.getFollowers(userId, 0, 50);

        // Assert
        assertThat(followers).isEmpty();
    }

    @Test
    void getFollowing_WhenUserFollowsNoOne_ReturnsEmptyList() {
        // Arrange
        String userId = createUser("hermit@example.com", "password123", "Hermit User");

        // Act
        var following = followerService.getFollowing(userId, 0, 50);

        // Assert
        assertThat(following).isEmpty();
    }

    private String createUser(String email, String password, String name) {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .name(name)
                .build();
        return authService.register(request).getUserId();
    }
}
