package com.ticketorchestra.common.api;

import lombok.Builder;

@Builder
public record ErrorResponse(String message, String code) {}
