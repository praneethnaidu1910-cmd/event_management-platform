package com.eventmanagement.controller;

import com.eventmanagement.dto.request.LoginRequest;
import com.eventmanagement.dto.request.RegisterRequest;
import com.eventmanagement.dto.response.AuthResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security is disabled for this slice test: it exercises the controller's own
 * request handling (routing, param binding, validation, exception mapping),
 * not the credential-checking or token-generation logic in AuthService/
 * JwtTokenProvider, which already have their own unit coverage.
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void registerReturns201WithNewUserId() throws Exception {
        User saved = User.builder().id(7L).email("new@example.com").role(User.Role.ATTENDEE).build();
        when(authService.registerUser(any())).thenReturn(saved);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.message").value("User registered"));
    }

    @Test
    void registerRejectsBlankEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("short");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: size must be between 6 and 2147483647"));
    }

    @Test
    void registerReturns400WhenEmailAlreadyRegistered() throws Exception {
        when(authService.registerUser(any())).thenThrow(new BadRequestException("Email already registered"));

        RegisterRequest request = new RegisterRequest();
        request.setEmail("taken@example.com");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void loginReturnsTokenOnSuccess() throws Exception {
        when(authService.authenticateUser(any()))
                .thenReturn(new AuthResponse("jwt-token", "user@example.com", "ATTENDEE"));

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("ATTENDEE"));
    }

    @Test
    void loginReturns401OnBadCredentials() throws Exception {
        when(authService.authenticateUser(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }

    @Test
    void loginRejectsInvalidEmailFormat() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("not-an-email");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
