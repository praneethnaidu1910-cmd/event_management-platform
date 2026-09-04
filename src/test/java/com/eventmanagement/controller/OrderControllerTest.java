package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockBean
    private OrderService orderService;

    @MockBean
    private CurrentUserService currentUserService;

    // Pulled in transitively by SecurityConfig's JwtAuthenticationFilter bean; unused by these
    // tests since @WithMockUser seeds the SecurityContext directly, bypassing the filter.
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    private PurchaseRequest sampleRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(3L);
        request.setQuantity(2);
        return request;
    }

    private OrderResponse sampleResponse() {
        return OrderResponse.builder()
                .orderId(9L)
                .totalAmount(new BigDecimal("100.00"))
                .tickets(List.of())
                .build();
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returnsOrder() throws Exception {
        User attendee = User.builder().id(2L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(9))
                .andExpect(jsonPath("$.totalAmount").value(100.00));

        verify(orderService).purchaseTickets(any(PurchaseRequest.class), eq(attendee));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void purchase_asOrganizer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    void purchase_withoutAuthentication_isForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_withMissingQuantity_returnsBadRequest() throws Exception {
        PurchaseRequest invalid = new PurchaseRequest();
        invalid.setTicketTypeId(3L);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).purchaseTickets(any(), any());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_whenTicketTypeMissing_returnsNotFound() throws Exception {
        User attendee = User.builder().id(2L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_whenNotEnoughTickets_returnsBadRequest() throws Exception {
        User attendee = User.builder().id(2L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isBadRequest());
    }
}
