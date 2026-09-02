package com.eventmanagement.service;

import com.eventmanagement.entity.User;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserServiceTest {

    private UserRepository userRepository;
    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        currentUserService = new CurrentUserService(userRepository);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User sampleUser() {
        return User.builder()
                .id(7L)
                .email("attendee@example.com")
                .role(User.Role.ATTENDEE)
                .build();
    }

    @Test
    void getCurrentUser_returnsUserForAuthenticatedPrincipal() {
        User user = sampleUser();
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail(eq(user.getEmail()))).thenReturn(Optional.of(user));

        User result = currentUserService.getCurrentUser();

        assertThat(result).isEqualTo(user);
    }

    @Test
    void getCurrentUser_throwsWhenNoAuthenticationIsSet() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getCurrentUser_throwsWhenAuthenticationIsNotAuthenticated() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "attendee@example.com", null, java.util.List.of());
        ((UsernamePasswordAuthenticationToken) authentication).setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getCurrentUser_throwsResourceNotFoundWhenNoUserMatchesTheAuthenticatedEmail() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "ghost@example.com", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail(eq("ghost@example.com"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
