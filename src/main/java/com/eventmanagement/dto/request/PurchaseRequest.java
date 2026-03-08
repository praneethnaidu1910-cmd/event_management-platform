package com.eventmanagement.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseRequest {
    @NotNull
    private Long ticketTypeId;

    @NotNull
    @Positive
    private Integer quantity;
}
