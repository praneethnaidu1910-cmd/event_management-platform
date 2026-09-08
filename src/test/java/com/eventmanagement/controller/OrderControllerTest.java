package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.repository.UserRepository;
import com.eventmanagement.security.JwtAuthenticationFilter;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
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
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private PurchaseRequest validPurchaseRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(5L);
        request.setQuantity(2);
        return request;
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returns200WithOrder() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        OrderResponse response = OrderResponse.builder()
                .orderId(11L)
                .totalAmount(new BigDecimal("100.00"))
                .tickets(List.of(
                        TicketResponse.builder().id(1L).ticketCode("code-1").status("ACTIVE").ticketTypeId(5L).build(),
                        TicketResponse.builder().id(2L).ticketCode("code-2").status("ACTIVE").ticketTypeId(5L).build()
                ))
                .build();
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee))).thenReturn(response);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(11))
                .andExpect(jsonPath("$.totalAmount").value(100.00))
                .andExpect(jsonPath("$.tickets.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void purchase_asOrganizer_returns403() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void purchase_whenAnonymous_returns403() throws Exception {
        // No AuthenticationEntryPoint is configured, so Spring Security falls back to
        // Http403ForbiddenEntryPoint for unauthenticated requests rather than a 401.
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_missingQuantity_returns400() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(5L);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_ticketTypeNotFound_returns404() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_insufficientTickets_returns400() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }
}
