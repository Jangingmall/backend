package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class ArtisanService {
    private final MemberAccess access;
    private final ArtisanProfileRepository artisans;
    private final MemberReadRepository reads;

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long artisanId) {
        return reads.artisan(artisanId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mine(Long memberId) {
        access.requireRole(memberId, MemberRole.ARTISAN);
        return detail(memberId);
    }

    @Transactional(readOnly = true)
    public CursorPage<Map<String, Object>> list(String cursor, int limit, String certification, String category, String initial, String sort) {
        return reads.artisans(cursor, limit, certification, category, initial, sort);
    }

    @Transactional
    public Map<String, Object> update(Long memberId, Changes changes) {
        access.lockRole(memberId, MemberRole.ARTISAN);
        var artisan = artisans.findById(memberId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        changes.category().ifPresent(this::requireCategory);
        artisan.updateBasics(changes.businessName(), changes.introduction(), changes.profileImageUrl(),
            changes.category(), changes.region(), changes.careerYears());
        artisan.updateBiography(changes.certifiedYear(), changes.lineage(), changes.quote(), changes.bio(), changes.videoUrl());
        artisans.save(artisan);
        artisans.flush();
        return detail(memberId);
    }

    private void requireCategory(String category) {
        if (!reads.categoryExists(category)) {
            throw new DomainException(ErrorCode.INVALID_INPUT);
        }
    }

    public record Changes(Optional<String> businessName, Optional<String> introduction, Optional<String> profileImageUrl,
                          Optional<String> category, Optional<String> region, Optional<Short> careerYears,
                          Optional<Short> certifiedYear, Optional<String> lineage, Optional<String> quote,
                          Optional<String> bio, Optional<String> videoUrl) {}
}
