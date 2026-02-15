package com.redesocial.userservice.service;

import com.redesocial.userservice.dto.UpdateProfileRequest;
import com.redesocial.userservice.dto.UserResponse;
import com.redesocial.userservice.exception.UserNotFoundException;
import com.redesocial.userservice.model.User;
import com.redesocial.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        return mapToUserResponse(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Update only provided fields
        if (request.getName() != null) {
            user.setName(request.getName());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getProfilePictureUrl() != null) {
            user.setProfilePictureUrl(request.getProfilePictureUrl());
        }
        if (request.getIsPrivate() != null) {
            user.setIsPrivate(request.getIsPrivate());
        }

        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Transactional
    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        userRepository.delete(user);
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
