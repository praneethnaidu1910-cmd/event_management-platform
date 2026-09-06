package com.eventmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private String message;
    private LocalDateTime timestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> fieldErrors;
}
