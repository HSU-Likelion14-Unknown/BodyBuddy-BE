package com.centerton.bodybuddy.domain.auth.service;

import com.centerton.bodybuddy.domain.auth.dto.AnonymousUserRes;
import com.centerton.bodybuddy.domain.auth.entity.IdempotencyKey;
import com.centerton.bodybuddy.domain.auth.repository.IdempotencyKeyRepository;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @InjectMocks private AuthService authService;

    @Test
    void createsAnonymousUserWithNonNullSharingDefault() {
        when(idempotencyKeyRepository.existsByIdempotencyKey("auth-key"))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AnonymousUserRes result = authService.createAnonymousUser("auth-key");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getShareToRoom()).isFalse();
        assertThat(result.getUserId()).isEqualTo(userCaptor.getValue().getUserId());
        assertThat(result.getAccessKey()).isNotBlank();
    }
}
