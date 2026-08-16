package com.centerton.bodybuddy.domain.user.service;

import com.centerton.bodybuddy.domain.auth.util.AuthValidator;
import com.centerton.bodybuddy.domain.user.dto.*;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import com.centerton.bodybuddy.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ImageStorage imageStorage;

    @Transactional
    public OnboardingRes saveOnboarding(String authorization, OnboardingReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        user.updateOnboarding(
                req.getNickname(),
                req.getBirthYear(),
                req.getGender(),
                req.getAllergyCodes(),
                req.getDislikedFoods(),
                LocalDateTime.now()
        );

        return OnboardingRes.builder()
                .nickname(user.getNickname())
                .birthYear(user.getBirthYear())
                .gender(user.getGender())
                .allergyCodes(req.getAllergyCodes())
                .dislikedFoods(req.getDislikedFoods())
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public UserInfoRes getMyInfo(String authorization) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        return UserInfoRes.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .birthYear(user.getBirthYear())
                .gender(user.getGender())
                .allergyCodes(user.getAllergyCodes())
                .dislikedFoods(user.getDislikedFoods())
                .shareToRoom(user.getShareToRoom())
                .profileImageUrl(user.getProfileImageUrl())
                .onboardingCompletedAt(user.getOnboardingCompletedAt())
                .build();
    }

    @Transactional
    public UserProfileUpdateRes updateProfile(String authorization, UserProfileUpdateReq req) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        user.updateProfile(
                req.getNickname(),
                req.getBirthYear(),
                req.getGender(),
                req.getAllergyCodes(),
                req.getDislikedFoods(),
                req.getShareToRoom()
        );

        return UserProfileUpdateRes.builder()
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .birthYear(user.getBirthYear())
                .gender(user.getGender())
                .allergyCodes(user.getAllergyCodes())
                .dislikedFoods(user.getDislikedFoods())
                .shareToRoom(user.getShareToRoom())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    @Transactional
    public ProfileImageUpdateRes updateProfileImage(String authorization, MultipartFile image) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);

        String profileImageUrl = imageStorage.store(image);
        user.updateProfileImage(profileImageUrl);

        return ProfileImageUpdateRes.builder()
                .userId(user.getUserId())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    @Transactional
    public void deleteUser(String authorization) {
        User user = AuthValidator.validateAndGetUser(authorization, userRepository);
        userRepository.delete(user);
    }
}