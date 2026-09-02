package com.eventmanagement.security;

import com.eventmanagement.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
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
        // validateToken does not catch parser exceptions itself; it relies on
        // the caller to handle them. JwtAuthenticationFilter does exactly
        // that (catches JwtException around this call), so this propagates
        // as an unauthenticated request rather than a 500 - see
        // JwtAuthenticationFilterTest for that behavior.
        assertThrows(JwtException.class, () -> tokenProvider.validateToken("not-a-real-token"));
    }

    @Test
    void validateToken_throwsForTokenSignedWithDifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider("a-completely-different-signing-secret-value", EXPIRATION_MS);
        String token = otherProvider.generateToken(sampleUser());

        assertThrows(JwtException.class, () -> tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_throwsExpiredJwtExceptionForExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, -1_000);
        String token = shortLivedProvider.generateToken(sampleUser());

        assertThrows(ExpiredJwtException.class, () -> tokenProvider.validateToken(token));
    }

    @Test
    void getUserIdFromToken_throwsExpiredJwtExceptionForExpiredToken() {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(SECRET, -1_000);
        String token = shortLivedProvider.generateToken(sampleUser());

        assertThrows(ExpiredJwtException.class, () -> tokenProvider.getUserIdFromToken(token));
    }
}
