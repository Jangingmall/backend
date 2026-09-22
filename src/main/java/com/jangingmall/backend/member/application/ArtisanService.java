package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.image.application.ImageService;
import com.jangingmall.backend.image.domain.ImagePurpose;
import com.jangingmall.backend.member.domain.*;
import com.jangingmall.backend.revalidate.domain.RevalidateEvent;
import com.jangingmall.backend.revalidate.domain.RevalidateEventType;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ArtisanService {
    private final MemberAccess access;
    private final ArtisanProfileRepository artisans;
    private final MemberReadRepository reads;
    private final ImageService images;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ArtisanService(MemberAccess access, ArtisanProfileRepository artisans, MemberReadRepository reads,
                          ImageService images, ApplicationEventPublisher eventPublisher) {
        this.access = access;
        this.artisans = artisans;
        this.reads = reads;
        this.images = images;
        this.eventPublisher = eventPublisher;
    }

    public ArtisanService(MemberAccess access, ArtisanProfileRepository artisans, MemberReadRepository reads) {
        this(access, artisans, reads, null, null);
    }

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
    public Page<Map<String, Object>> list(Pageable pageable, String certification, String category, String initial, String sort) {
        return reads.artisans(pageable, certification, category, initial, sort);
    }

    @Transactional
    public Map<String, Object> update(Long memberId, Changes changes) {
        access.lockRole(memberId, MemberRole.ARTISAN);
        var artisan = artisans.findById(memberId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        changes.category().ifPresent(this::requireCategory);
        artisan.updateBasics(changes.businessName(), changes.introduction(), changes.profileImageUrl(),
            changes.category(), changes.region(), changes.careerYears());
        changes.profileImageId().ifPresent(imageId -> {
            if (images == null) {
                throw new DomainException(ErrorCode.INVALID_INPUT);
            }
            images.consumeOwned(memberId, ImagePurpose.ARTISAN, List.of(imageId));
            String imageUrl = images.publicVariants(imageId).stream()
                .filter(variant -> variant.width() == 640)
                .map(ImageService.PublicVariant::url)
                .findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_INPUT));
            artisan.updateProfileImage(imageId, imageUrl);
        });
        artisan.updateBiography(changes.certifiedYear(), changes.lineage(), changes.quote(), changes.bio(), changes.videoUrl());
        artisans.save(artisan);
        artisans.flush();
        eventPublisher.publishEvent(RevalidateEvent.ofArtisan(RevalidateEventType.ARTISAN_UPDATED, UUID.randomUUID().toString(), Instant.now(), memberId));
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
                          Optional<String> bio, Optional<String> videoUrl, Optional<String> profileImageId) {
        public Changes(Optional<String> businessName, Optional<String> introduction, Optional<String> profileImageUrl,
                       Optional<String> category, Optional<String> region, Optional<Short> careerYears,
                       Optional<Short> certifiedYear, Optional<String> lineage, Optional<String> quote,
                       Optional<String> bio, Optional<String> videoUrl) {
            this(businessName, introduction, profileImageUrl, category, region, careerYears, certifiedYear,
                lineage, quote, bio, videoUrl, Optional.empty());
        }
    }
}
