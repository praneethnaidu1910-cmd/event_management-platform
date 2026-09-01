package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(OrderControllerTest.MethodSecurityConfig.class)
class OrderControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private PurchaseRequest validRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(5L);
        request.setQuantity(2);
        return request;
    }

    @Test
    @WithMockUser(username = "attendee@example.com", roles = "ATTENDEE")
    void purchase_returnsOrderOnSuccess() throws Exception {
        User attendee = User.builder().id(1L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);

        OrderResponse response = OrderResponse.builder()
                .orderId(100L)
                .totalAmount(new BigDecimal("50.00"))
                .tickets(List.of(TicketResponse.builder()
                        .id(1L)
                        .ticketCode("code-1")
                        .status("ACTIVE")
                        .ticketTypeId(5L)
                        .build()))
                .build();
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee))).thenReturn(response);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.totalAmount").value(50.00))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("code-1"));
    }

    @Test
    @WithMockUser(username = "attendee@example.com", roles = "ATTENDEE")
    void purchase_returnsBadRequestWhenNotEnoughTicketsAvailable() throws Exception {
        User attendee = User.builder().id(1L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), any(User.class)))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }

    @Test
    @WithMockUser(username = "attendee@example.com", roles = "ATTENDEE")
    void purchase_rejectsMissingQuantityWithBadRequest() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(5L);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    @WithMockUser(username = "organizer@example.com", roles = "ORGANIZER")
    void purchase_rejectsNonAttendeeWithForbidden() throws Exception {
        mockMvc.perform(post("/api/orders/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(orderService);
    }
}
