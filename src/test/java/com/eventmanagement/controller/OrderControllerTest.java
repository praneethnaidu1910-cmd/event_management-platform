package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.InsufficientTicketsException;
import com.eventmanagement.exception.ResourceNotFoundException;
import com.eventmanagement.security.JwtAuthenticationFilter;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security is disabled for this slice test: it exercises the controller's own
 * request handling (routing, param binding, validation, exception mapping),
 * not the {@code @PreAuthorize} rule enforced by SecurityConfig or the
 * transactional inventory logic in OrderService, which has its own coverage
 * in OrderServiceTest.
 */
@WebMvcTest(
        controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void purchaseReturnsOrderOnSuccess() throws Exception {
        User attendee = User.builder().id(5L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(), any())).thenReturn(sampleOrder());

        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);
        request.setQuantity(2);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.totalAmount").value(50.0))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("TCK-1"));
    }

    @Test
    void purchaseRejectsMissingQuantity() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quantity: must not be null"));
    }

    @Test
    void purchaseRejectsNonPositiveQuantity() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);
        request.setQuantity(0);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quantity: must be greater than 0"));
    }

    @Test
    void purchaseReturns400WhenNotEnoughTickets() throws Exception {
        User attendee = User.builder().id(5L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(), any()))
                .thenThrow(new InsufficientTicketsException("Not enough tickets available"));

        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);
        request.setQuantity(2);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough tickets available"));
    }

    @Test
    void purchaseReturns404WhenTicketTypeMissing() throws Exception {
        User attendee = User.builder().id(5L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(), any()))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(99L);
        request.setQuantity(1);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket type not found"));
    }

    private OrderResponse sampleOrder() {
        return OrderResponse.builder()
                .orderId(100L)
                .totalAmount(BigDecimal.valueOf(50.0))
                .tickets(List.of(TicketResponse.builder()
                        .id(1L)
                        .ticketCode("TCK-1")
                        .status("CONFIRMED")
                        .ticketTypeId(1L)
                        .build()))
                .build();
    }
}
