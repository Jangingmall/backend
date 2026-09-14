package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.common.response.ApiResponse;
import com.jangingmall.backend.member.application.AddressData;
import com.jangingmall.backend.member.application.AddressService;
import com.jangingmall.backend.member.presentation.dto.MemberAccountRequests;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member/me/addresses")
@RequiredArgsConstructor
public class AddressController {
    private final AddressService addresses;

    @GetMapping
    public List<AddressData> list(@AuthenticationPrincipal Long memberId) {
        return addresses.list(memberId);
    }

    @PostMapping
    public ResponseEntity<AddressData> create(@AuthenticationPrincipal Long memberId,
                                              @Valid @RequestBody MemberAccountRequests.CreateAddress request) {
        return ResponseEntity.status(201).body(addresses.create(memberId, request.toData()));
    }

    @PatchMapping("/{addressId}")
    public AddressData update(@AuthenticationPrincipal Long memberId, @PathVariable Long addressId,
                              @Valid @RequestBody MemberAccountRequests.UpdateAddress request) {
        return addresses.update(memberId, addressId, request.toChanges());
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long memberId, @PathVariable Long addressId) {
        addresses.delete(memberId, addressId);
        return ApiResponse.ok(null);
    }
}
