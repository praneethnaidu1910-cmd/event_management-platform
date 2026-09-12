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
                .orderId(10L)
                .totalAmount(BigDecimal.valueOf(50))
                .tickets(List.of(TicketResponse.builder()
                        .id(1L)
                        .ticketCode("TICKET-1")
                        .status("VALID")
                        .ticketTypeId(3L)
                        .build()))
                .build();
    }

    private String purchaseRequestJson(long ticketTypeId, int quantity) throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(ticketTypeId);
        request.setQuantity(quantity);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returnsOrder() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(1L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class))).thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("TICKET-1"));
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
    void purchase_notEnoughTicketsAvailable_returnsBadRequest() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(1L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(3L, 500)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_unknownTicketType_returnsNotFound() throws Exception {
        when(currentUserService.getCurrentUser()).thenReturn(attendee(1L));
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(purchaseRequestJson(999L, 1)))
                .andExpect(status().isNotFound());
    }
}
