package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses @SpringBootTest rather than the @WebMvcTest slice for the same reason
 * as EventControllerTest: the slice skips SecurityConfig's method security,
 * and the @PreAuthorize("hasRole('ATTENDEE')") check on purchase() is exactly
 * what these tests need to exercise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CurrentUserService currentUserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User attendee(long id) {
        return User.builder().id(id).email("attendee@example.com").role(User.Role.ATTENDEE).build();
    }

    private OrderResponse orderResponse() {
        return OrderResponse.builder()
                .orderId(1L)
                .totalAmount(new BigDecimal("50.00"))
                .tickets(List.of(TicketResponse.builder()
                        .id(1L)
                        .ticketCode("code-1")
                        .status("ACTIVE")
                        .ticketTypeId(3L)
                        .build()))
                .build();
    }

    private String purchaseRequestJson(Long ticketTypeId, Integer quantity) throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(ticketTypeId);
        request.setQuantity(quantity);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returnsOk() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(5L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class))).thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("code-1"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void purchase_asOrganizer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 2)))
                .andExpect(status().isForbidden());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    void purchase_unauthenticated_isForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 2)))
                .andExpect(status().isForbidden());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_insufficientTickets_returnsBadRequest() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(5L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 50)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_ticketTypeNotFound_returnsNotFound() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(5L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(99L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket type not found"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_nonPositiveQuantity_isBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 0)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_missingTicketTypeId_isBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(null, 2)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).purchaseTickets(any(), any());
    }
}
