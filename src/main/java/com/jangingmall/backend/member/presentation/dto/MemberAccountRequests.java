package com.jangingmall.backend.member.presentation.dto;

import com.jangingmall.backend.member.application.AddressData;
import com.jangingmall.backend.member.application.AddressService;
import jakarta.validation.constraints.*;
import java.util.Optional;

public final class MemberAccountRequests {
    private MemberAccountRequests() {}

    public record Profile(@Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String name,
                          @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String nickname,
                          @Pattern(regexp = "\\d{9,20}") String phone) {}

    public record Password(@NotBlank @Size(max = 72) String currentPassword,
                           @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    public record Withdrawal(@NotBlank @Size(max = 500) String reason) {}

    public record CreateAddress(@NotBlank @Size(max = 50) String recipientName,
                                @NotBlank @Pattern(regexp = "\\d{9,20}") String phone,
                                @NotBlank @Size(max = 10) String zipCode,
                                @NotBlank @Size(max = 255) String address1,
                                @Size(max = 255) String address2,
                                @NotNull Boolean isDefault) {
        public AddressData toData() {
            return new AddressData(null, recipientName, phone, zipCode, address1, address2, isDefault);
        }
    }

    public record UpdateAddress(@Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*") String recipientName,
                                @Pattern(regexp = "\\d{9,20}") String phone,
                                @Size(min = 1, max = 10) @Pattern(regexp = ".*\\S.*") String zipCode,
                                @Size(min = 1, max = 255) @Pattern(regexp = ".*\\S.*") String address1,
                                @Size(max = 255) String address2, Boolean isDefault) {
        public AddressService.Changes toChanges() {
            return new AddressService.Changes(Optional.ofNullable(recipientName), Optional.ofNullable(phone),
                Optional.ofNullable(zipCode), Optional.ofNullable(address1), Optional.ofNullable(address2),
                Optional.ofNullable(isDefault));
        }
    }
}
