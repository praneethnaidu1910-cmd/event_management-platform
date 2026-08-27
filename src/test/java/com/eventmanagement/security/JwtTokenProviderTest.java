package com.eventmanagement.security;

import com.eventmanagement.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256";
    private static final long EXPIRATION_MS = 60_000;

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    private User sampleUser() {
        return User.builder()
                .id(42L)
                .email("attendee@example.com")
                .role(User.Role.ATTENDEE)
                .build();
    }

    @Test
    void generateToken_roundTripsUserId() {
        String token = tokenProvider.generateToken(sampleUser());

        assertThat(tokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    void validateToken_returnsTrueForFreshlyIssuedToken() {
        String token = tokenProvider.generateToken(sampleUser());

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_throwsForMalformedToken() {
        // Documents current behavior: validateToken does not catch parser
        // exceptions, so a bad token propagates instead of returning false.
        // JwtAuthenticationFilter calls this unguarded, so a malformed
        // Authorization header currently 500s the request instead of just
        // being treated as unauthenticated.
        assertThrows(JwtException.class, () -> tokenProvider.validateToken("not-a-real-token"));
    }

    @Test
    void validateToken_throwsForTokenSignedWithDifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider("a-completely-different-signing-secret-value", EXPIRATION_MS);
        String token = otherProvider.generateToken(sampleUser());

        assertThrows(JwtException.class, () -> tokenProvider.validateToken(token));
    }
}
