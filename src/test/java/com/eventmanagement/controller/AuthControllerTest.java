package com.eventmanagement.controller;

import com.eventmanagement.dto.request.LoginRequest;
import com.eventmanagement.dto.request.RegisterRequest;
import com.eventmanagement.dto.response.AuthResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
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
@Import(SecurityConfig.class)
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
    private UserDetailsServiceImpl userDetailsService;

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);
        return request;
    }

    private LoginRequest validLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password123");
        return request;
    }

    @Test
    void register_withValidRequest_returnsCreated() throws Exception {
        User saved = User.builder().id(3L).email("new@example.com").role(User.Role.ATTENDEE).build();
        when(authService.registerUser(any(RegisterRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.message").value("User registered"));
    }

    @Test
    void register_duplicateEmail_returnsBadRequest() throws Exception {
        when(authService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_passwordTooShort_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_missingRequiredFields_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withValidCredentials_returnsOk() throws Exception {
        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("signed-jwt", "attendee@example.com", "ATTENDEE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.email").value("attendee@example.com"))
                .andExpect(jsonPath("$.role").value("ATTENDEE"));
    }

    @Test
    void login_withBadCredentials_returnsUnauthorized() throws Exception {
        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_userRecordMissingAfterAuthentication_returnsNotFound() throws Exception {
        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validLoginRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    void login_missingFields_returnsBadRequest() throws Exception {
        LoginRequest request = new LoginRequest();

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
