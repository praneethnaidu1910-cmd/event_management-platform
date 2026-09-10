package com.eventmanagement.security;

import com.eventmanagement.entity.User;
import com.eventmanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link SecurityConfig} filter chain end to end (real JWT
 * filter, real {@code @PreAuthorize} checks, H2 standing in for Postgres) rather
 * than mocking security away, since the per-controller unit tests elsewhere
 * don't verify that the chain's URL rules and role checks actually agree with
 * what each controller declares.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenFor(User.Role role, String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .firstName("Test")
                .lastName("User")
                .role(role)
                .build());
        return jwtTokenProvider.generateToken(user);
    }

    @Test
    void browsingEvents_isPublic() throws Exception {
        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk());
    }

    @Test
    void searchingEvents_isPublic() throws Exception {
        mockMvc.perform(get("/api/events/search"))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpoints_areReachableWithoutAToken() throws Exception {
        // An empty body fails bean validation (400), which still proves the
        // request got past the security filter chain instead of being
        // rejected for lack of authentication (401/403).
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void graphQlEndpoint_isPublic() throws Exception {
        mockMvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ events { id } }\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void creatingAnEvent_withoutAToken_isRejected() throws Exception {
        // No custom AuthenticationEntryPoint is configured, so Spring Security's
        // default Http403ForbiddenEntryPoint answers unauthenticated requests
        // with 403 rather than 401.
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAnEvent_withAnAttendeeToken_isForbidden() throws Exception {
        String token = tokenFor(User.Role.ATTENDEE, "attendee@example.com");

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingAnEvent_withAnOrganizerToken_succeeds() throws Exception {
        String token = tokenFor(User.Role.ORGANIZER, "organizer@example.com");

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void purchasingTickets_withAnOrganizerToken_isForbidden() throws Exception {
        // A well-formed body so the request fails on the @PreAuthorize role
        // check rather than on request validation, which runs first.
        String token = tokenFor(User.Role.ORGANIZER, "organizer2@example.com");

        mockMvc.perform(post("/api/orders/purchase")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketTypeId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithAGarbledToken_isTreatedAsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer not-a-real-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validEventJson()))
                .andExpect(status().isForbidden());
    }

    private String validEventJson() {
        return "{"
                + "\"title\":\"Test Event\","
                + "\"location\":\"Test Venue\","
                + "\"startDate\":\"2027-01-01T10:00:00\","
                + "\"endDate\":\"2027-01-01T18:00:00\""
                + "}";
    }
}
