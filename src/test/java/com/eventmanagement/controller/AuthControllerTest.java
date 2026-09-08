package com.eventmanagement.controller;

import com.eventmanagement.dto.request.LoginRequest;
import com.eventmanagement.dto.request.RegisterRequest;
import com.eventmanagement.dto.response.AuthResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.security.JwtTokenProvider;
import com.eventmanagement.security.SecurityConfig;
import com.eventmanagement.security.UserDetailsServiceImpl;
import com.eventmanagement.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    void register_returns201WithUserId() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new.attendee@example.com");
        request.setPassword("password123");
        request.setFirstName("New");
        request.setLastName("Attendee");
        request.setRole(User.Role.ATTENDEE);

        User saved = User.builder().id(42L).email(request.getEmail()).role(User.Role.ATTENDEE).build();
        when(authService.registerUser(any(RegisterRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.message").value("User registered"));
    }

    @Test
    void register_returns400WhenEmailAlreadyRegistered() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("password123");
        request.setFirstName("Existing");
        request.setLastName("User");
        request.setRole(User.Role.ATTENDEE);

        when(authService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void register_returns400OnInvalidPayload() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("short");
        request.setFirstName("");
        request.setLastName("");
        request.setRole(null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password123");

        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-token", "attendee@example.com", "ATTENDEE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("attendee@example.com"))
                .andExpect(jsonPath("$.role").value("ATTENDEE"));
    }

    @Test
    void login_returns401OnBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("wrong-password");

        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
