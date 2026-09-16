package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtTokenProvider;
import com.eventmanagement.security.SecurityConfig;
import com.eventmanagement.security.UserDetailsServiceImpl;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private PurchaseRequest validPurchaseRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(5L);
        request.setQuantity(2);
        return request;
    }

    private OrderResponse orderResponse() {
        return OrderResponse.builder()
                .orderId(1L)
                .totalAmount(new BigDecimal("100.00"))
                .tickets(List.of(
                        TicketResponse.builder()
                                .id(1L)
                                .ticketCode("ticket-code-1")
                                .status("ACTIVE")
                                .ticketTypeId(5L)
                                .build(),
                        TicketResponse.builder()
                                .id(2L)
                                .ticketCode("ticket-code-2")
                                .status("ACTIVE")
                                .ticketTypeId(5L)
                                .build()
                ))
                .build();
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returnsOk() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), org.mockito.ArgumentMatchers.eq(attendee)))
                .thenReturn(orderResponse());

        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("ticket-code-1"))
                .andExpect(jsonPath("$.tickets[1].ticketCode").value("ticket-code-2"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void purchase_asOrganizer_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchase_unauthenticated_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_missingTicketTypeId_returnsBadRequest() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setQuantity(2);

        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_nonPositiveQuantity_returnsBadRequest() throws Exception {
        PurchaseRequest request = validPurchaseRequest();
        request.setQuantity(0);

        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_ticketTypeNotFound_returnsNotFound() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), org.mockito.ArgumentMatchers.eq(attendee)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket type not found"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_insufficientTickets_returnsBadRequest() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), org.mockito.ArgumentMatchers.eq(attendee)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }
}
