package com.eventmanagement.controller;

import com.eventmanagement.dto.response.AdminOrderResponse;
import com.eventmanagement.dto.response.PlatformStatsResponse;
import com.eventmanagement.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/orders")
    public ResponseEntity<List<AdminOrderResponse>> listAllOrders() {
        return ResponseEntity.ok(adminService.listAllOrders());
    }

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getPlatformStats());
    }
}
