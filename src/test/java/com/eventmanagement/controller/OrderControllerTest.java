package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.dto.response.TicketResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.exception.GlobalExceptionHandler;
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

/**
 * Exercises OrderController through MockMvc, the same slice-test pattern
 * used for AuthController. The purchase flow itself (inventory locking,
 * oversell prevention) is OrderService's concern and already has its own
 * unit tests; this only checks the controller's request/response mapping
 * and error translation. The @PreAuthorize("hasRole('ATTENDEE')") check
 * lives in SecurityConfig and isn't exercised here - the filter chain is
 * disabled, same as in AuthControllerTest.
 */
@WebMvcTest(
        controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
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
    @WithMockUser(roles = "ATTENDEE")
    void purchase_asAttendee_returnsOrder() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee))).thenReturn(sampleOrderResponse());

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.totalAmount").value(199.98))
                .andExpect(jsonPath("$.tickets[0].ticketCode").value("TICKET-CODE"));
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_rejectsInvalidPayloadWithBadRequest() throws Exception {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);
        request.setQuantity(0);

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_returnsBadRequestWhenNotEnoughTickets() throws Exception {
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

    @Test
    @WithMockUser(roles = "ATTENDEE")
    void purchase_returnsNotFoundWhenTicketTypeMissing() throws Exception {
        User attendee = User.builder().id(3L).email("attendee@example.com").role(User.Role.ATTENDEE).build();
        when(currentUserService.getCurrentUser()).thenReturn(attendee);
        when(orderService.purchaseTickets(any(PurchaseRequest.class), eq(attendee)))
                .thenThrow(new ResourceNotFoundException("Ticket type not found"));

        mockMvc.perform(post("/api/orders/purchase")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validPurchaseRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ticket type not found"));
    }

    private PurchaseRequest validPurchaseRequest() {
        PurchaseRequest request = new PurchaseRequest();
        request.setTicketTypeId(1L);
        request.setQuantity(2);
        return request;
    }

    private OrderResponse sampleOrderResponse() {
        TicketResponse ticket = TicketResponse.builder()
                .id(100L)
                .ticketCode("TICKET-CODE")
                .status("ACTIVE")
                .ticketTypeId(1L)
                .build();

        return OrderResponse.builder()
                .orderId(42L)
                .totalAmount(BigDecimal.valueOf(199.98))
                .tickets(List.of(ticket))
                .build();
    }
}
