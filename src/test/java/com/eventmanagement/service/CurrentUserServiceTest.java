package com.eventmanagement.service;

import com.eventmanagement.entity.User;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsUserMatchingAuthenticatedPrincipalEmail() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("attendee@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = User.builder()
                .id(3L)
                .email("attendee@example.com")
                .role(User.Role.ATTENDEE)
                .build();
        when(userRepository.findByEmail("attendee@example.com")).thenReturn(Optional.of(user));

        User result = currentUserService.getCurrentUser();

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getEmail()).isEqualTo("attendee@example.com");
    }

    @Test
    void getCurrentUser_throwsWhenNoAuthenticationIsPresent() {
        SecurityContextHolder.getContext().setAuthentication(null);

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getCurrentUser_throwsWhenAuthenticationIsNotAuthenticated() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getCurrentUser_throwsWhenAuthenticatedEmailHasNoMatchingUserRecord() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("ghost@example.com");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserService.getCurrentUser())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
