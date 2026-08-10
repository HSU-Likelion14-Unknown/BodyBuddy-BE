package com.centerton.bodybuddy.domain.user.repository;

import com.centerton.bodybuddy.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}