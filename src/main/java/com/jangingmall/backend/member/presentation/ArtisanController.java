package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.member.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/member/artisans") @RequiredArgsConstructor
public class ArtisanController {
    private final ArtisanService artisans;

    @GetMapping
    public CursorPage<Map<String, Object>> list(@RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String certificationLevel,
            @RequestParam(required = false) String category, @RequestParam(required = false) String initial,
            @RequestParam(defaultValue = "POPULAR") String sort) {
        return artisans.list(cursor, limit, certificationLevel, category, initial, sort);
    }

    @GetMapping("/{artisanId}")
    public Map<String, Object> detail(@PathVariable Long artisanId) {
        return artisans.detail(artisanId);
    }

    @GetMapping("/me")
    public Map<String, Object> mine(@AuthenticationPrincipal Long memberId) {
        return artisans.mine(memberId);
    }

    @PatchMapping("/me")
    public Map<String, Object> update(@AuthenticationPrincipal Long memberId, @Valid @RequestBody Profile request) {
        return artisans.update(memberId, request.toChanges());
    }

    public record Profile(@Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*") String businessName,
            @Size(max = 255) String introduction, @Size(max = 500) @Pattern(regexp = "https://[^\\s]+") String profileImageUrl,
            @Size(min = 1, max = 50) String category, @Size(max = 100) String region,
            @Min(0) @Max(200) Short careerYears, @Min(1900) @Max(2200) Short certifiedYear,
            @Size(max = 255) String lineage, @Size(max = 500) String quote, @Size(max = 65535) String bio,
            @Size(max = 500) @Pattern(regexp = "https://[^\\s]+") String videoUrl) {
        public ArtisanService.Changes toChanges() {
            return new ArtisanService.Changes(Optional.ofNullable(businessName), Optional.ofNullable(introduction),
                Optional.ofNullable(profileImageUrl), Optional.ofNullable(category), Optional.ofNullable(region),
                Optional.ofNullable(careerYears), Optional.ofNullable(certifiedYear), Optional.ofNullable(lineage),
                Optional.ofNullable(quote), Optional.ofNullable(bio), Optional.ofNullable(videoUrl));
        }
    }
}
