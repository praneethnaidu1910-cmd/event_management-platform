package com.eventmanagement.controller;

import com.eventmanagement.dto.request.PurchaseRequest;
import com.eventmanagement.dto.response.OrderResponse;
import com.eventmanagement.entity.User;
import com.eventmanagement.service.CurrentUserService;
import com.eventmanagement.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<OrderResponse> purchase(@Valid @RequestBody PurchaseRequest request) {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(orderService.purchaseTickets(request, user));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<List<OrderResponse>> getMyOrders() {
        User user = currentUserService.getCurrentUser();
        return ResponseEntity.ok(orderService.getOrdersForUser(user));
    }
}
