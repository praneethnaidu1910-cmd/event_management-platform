package com.eventmanagement.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handlesResourceNotFound() {
        ResponseEntity<?> response = handler.handleNotFound(new ResourceNotFoundException("Event not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(bodyMessage(response)).isEqualTo("Event not found");
    }

    @Test
    void handlesBadRequest() {
        ResponseEntity<?> response = handler.handleBadRequest(new BadRequestException("Email already registered"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyMessage(response)).isEqualTo("Email already registered");
    }

    @Test
    void handlesInsufficientTickets() {
        ResponseEntity<?> response = handler.handleInsufficient(new InsufficientTicketsException("Not enough tickets available"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bodyMessage(response)).isEqualTo("Not enough tickets available");
    }

    @Test
    void handlesAccessDenied() {
        ResponseEntity<?> response = handler.handleAccessDenied(new AccessDeniedException("Not the event organizer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyMessage(response)).isEqualTo("Not the event organizer");
    }

    @Test
    void handlesAuthorizationDenied() {
        ResponseEntity<?> response = handler.handleAuthorizationDenied(
                new org.springframework.security.access.AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyMessage(response)).isEqualTo("Access Denied");
    }

    @Test
    void handlesAuthenticationFailure() {
        ResponseEntity<?> response = handler.handleAuthentication(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyMessage(response)).isEqualTo("Bad credentials");
    }

    @Test
    void genericHandlerHidesRawExceptionMessageFromClients() {
        ResponseEntity<?> response = handler.handleGeneric(new RuntimeException("password_hash column violates not-null constraint"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(bodyMessage(response)).isEqualTo("Unexpected error");
    }

    private String bodyMessage(ResponseEntity<?> response) {
        Object body = response.getBody();
        assertThat(body).isInstanceOf(com.eventmanagement.dto.response.ErrorResponse.class);
        return ((com.eventmanagement.dto.response.ErrorResponse) body).getMessage();
    }
}
