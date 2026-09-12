package com.eventmanagement.controller;

import com.eventmanagement.dto.request.LoginRequest;
import com.eventmanagement.dto.request.RegisterRequest;
import com.eventmanagement.dto.response.AuthResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.BadRequestException;
import com.eventmanagement.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String registerRequestJson() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("password123");
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setRole(User.Role.ATTENDEE);
        return objectMapper.writeValueAsString(request);
    }

    private String loginRequestJson(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void register_withNewEmail_returnsCreated() throws Exception {
        User saved = User.builder().id(7L).email("new@example.com").role(User.Role.ATTENDEE).build();
        when(authService.registerUser(any())).thenReturn(saved);

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.message").value("User registered"));
    }

    @Test
    void register_withDuplicateEmail_returnsBadRequest() throws Exception {
        when(authService.registerUser(any())).thenThrow(new BadRequestException("Email already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(registerRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        when(authService.authenticateUser(any()))
                .thenReturn(new AuthResponse("signed-jwt", "attendee@example.com", "ATTENDEE"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequestJson("attendee@example.com", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.role").value("ATTENDEE"));
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() throws Exception {
        when(authService.authenticateUser(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(loginRequestJson("attendee@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }
}
