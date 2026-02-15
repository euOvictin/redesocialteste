package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.UserResponse;
import com.redesocial.userservice.exception.CannotFollowSelfException;
import com.redesocial.userservice.exception.UserNotFoundException;
import com.redesocial.userservice.model.Follower;
import com.redesocial.userservice.model.User;
import com.redesocial.userservice.repository.FollowerRepository;
import com.redesocial.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowerService {

    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;

    @Transactional
    public void followUser(String followerId, String followingId) {
        // Validate: cannot follow self
        if (followerId.equals(followingId)) {
            throw new CannotFollowSelfException("User cannot follow themselves");
        }

        // Verify both users exist
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new UserNotFoundException("Follower not found: " + followerId));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new UserNotFoundException("User to follow not found: " + followingId));

        // Check if already following (idempotent operation)
        if (followerRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return; // Already following, do nothing
        }

        // Create follower relationship
        Follower followerRelation = Follower.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();
        followerRepository.save(followerRelation);

        // Update denormalized counters
        follower.setFollowingCount(follower.getFollowingCount() + 1);
        following.setFollowersCount(following.getFollowersCount() + 1);
        userRepository.save(follower);
        userRepository.save(following);
    }

    @Transactional
    public void unfollowUser(String followerId, String followingId) {
        // Find the relationship
        Follower followerRelation = followerRepository
                .findByFollowerIdAndFollowingId(followerId, followingId)
                .orElse(null);

        if (followerRelation == null) {
            return; // Not following, do nothing (idempotent)
        }

        // Delete the relationship
        followerRepository.delete(followerRelation);

        // Update denormalized counters
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new UserNotFoundException("Follower not found: " + followerId));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + followingId));

        follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
        following.setFollowersCount(Math.max(0, following.getFollowersCount() - 1));
        userRepository.save(follower);
        userRepository.save(following);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getFollowers(String userId, int page, int size) {
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        // Get followers with pagination (50 per page as per spec)
        Pageable pageable = PageRequest.of(page, size);
        Page<Follower> followersPage = followerRepository.findByFollowingId(userId, pageable);

        // Map to UserResponse
        return followersPage.getContent().stream()
                .map(follower -> {
                    User user = userRepository.findById(follower.getFollowerId())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + follower.getFollowerId()));
                    return mapToUserResponse(user);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getFollowing(String userId, int page, int size) {
        // Verify user exists
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        // Get following with pagination (50 per page as per spec)
        Pageable pageable = PageRequest.of(page, size);
        Page<Follower> followingPage = followerRepository.findByFollowerId(userId, pageable);

        // Map to UserResponse
        return followingPage.getContent().stream()
                .map(follower -> {
                    User user = userRepository.findById(follower.getFollowingId())
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + follower.getFollowingId()));
                    return mapToUserResponse(user);
                })
                .collect(Collectors.toList());
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .bio(user.getBio())
                .profilePictureUrl(user.getProfilePictureUrl())
                .followersCount(user.getFollowersCount())
                .followingCount(user.getFollowingCount())
                .isPrivate(user.getIsPrivate())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
