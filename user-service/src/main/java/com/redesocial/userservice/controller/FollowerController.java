package com.redesocial.userservice.controller;

import com.redesocial.userservice.dto.UserResponse;
import com.redesocial.userservice.service.FollowerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FollowerController {

    private final FollowerService followerService;

    @PostMapping("/{userId}/follow")
    public ResponseEntity<Void> followUser(
            @PathVariable String userId,
            @RequestParam String followerId
    ) {
        followerService.followUser(followerId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<Void> unfollowUser(
            @PathVariable String userId,
            @RequestParam String followerId
    ) {
        followerService.unfollowUser(followerId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<UserResponse>> getFollowers(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<UserResponse> followers = followerService.getFollowers(userId, page, size);
        return ResponseEntity.ok(followers);
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<UserResponse>> getFollowing(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        List<UserResponse> following = followerService.getFollowing(userId, page, size);
        return ResponseEntity.ok(following);
    }
}
