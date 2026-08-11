package com.centerton.bodybuddy.domain.user.repository;

import com.centerton.bodybuddy.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByAccessKeyHash(String accessKeyHash);
}