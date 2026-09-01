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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void register_returnsCreatedWithUserId() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password1");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        User savedUser = User.builder()
                .id(42L)
                .email("attendee@example.com")
                .firstName("Ada")
                .lastName("Lovelace")
                .role(User.Role.ATTENDEE)
                .build();
        when(authService.registerUser(any(RegisterRequest.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.message").value("User registered"));
    }

    @Test
    void register_rejectsInvalidEmailWithBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("not-an-email");
        request.setPassword("password1");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_rejectsDuplicateEmailWithBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password1");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);

        when(authService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void login_returnsTokenOnSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("password1");

        when(authService.authenticateUser(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("jwt-token", "attendee@example.com", "ATTENDEE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("attendee@example.com"))
                .andExpect(jsonPath("$.role").value("ATTENDEE"));
    }

    @Test
    void login_rejectsBlankPasswordWithBadRequest() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("attendee@example.com");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
