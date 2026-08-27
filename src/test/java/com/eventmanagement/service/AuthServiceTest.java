package com.eventmanagement.service;

import com.eventmanagement.dto.request.LoginRequest;
import com.eventmanagement.dto.request.RegisterRequest;
import com.eventmanagement.dto.response.AuthResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private JwtTokenProvider tokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        tokenProvider = mock(JwtTokenProvider.class);
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, tokenProvider);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);
        return request;
    }

    @Test
    void registerUser_savesUserWithEncodedPassword() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = authService.registerUser(request);

        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(saved.getRole()).isEqualTo(User.Role.ATTENDEE);
    }

    @Test
    void registerUser_rejectsDuplicateEmail() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail("new@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerUser(request))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void authenticateUser_returnsTokenForKnownUser() {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("attendee@example.com");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);

        User user = User.builder()
                .id(1L)
                .email("attendee@example.com")
                .role(User.Role.ATTENDEE)
                .build();
        when(userRepository.findByEmail("attendee@example.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(user)).thenReturn("signed-jwt");

        AuthResponse response = authService.authenticateUser(request);

        assertThat(response.getToken()).isEqualTo("signed-jwt");
        assertThat(response.getEmail()).isEqualTo("attendee@example.com");
        assertThat(response.getRole()).isEqualTo("ATTENDEE");
    }

    @Test
    void authenticateUser_throwsWhenAuthenticatedEmailHasNoMatchingUserRecord() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("ghost@example.com");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticateUser(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
