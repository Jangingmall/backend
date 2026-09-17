package com.jangingmall.backend.content.presentation;

import jakarta.validation.constraints.NotNull;

public sealed interface ContentRequest permits ContentRequest.Approve {

    record Approve(
        @NotNull Boolean factCheckConfirmed,
        @NotNull Boolean photoMatchConfirmed,
        @NotNull Boolean displayApprovalBadge
    ) implements ContentRequest {}
}
