package com.jangingmall.backend.member.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "artisan_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtisanProfile {
    @Id @Column(name = "artisan_id") private Long id;
    @Column(name = "business_name", nullable = false, length = 100) private String businessName;
    @Column(length = 255) private String introduction;
    @Column(name = "profile_image_id", length = 30) private String profileImageId;
    @Column(name = "profile_image_url", length = 500) private String profileImageUrl;
    @Column(name = "certification_level", nullable = false, length = 20) private String certificationLevel = "일반";
    @Column(name = "is_organization", nullable = false) private boolean organization;
    @Column(name = "certified_year") private Short certifiedYear;
    @Column(length = 255) private String lineage;
    @Column(length = 500) private String quote;
    @Column(length = 65535) private String bio;
    @Column(name = "video_url", length = 500) private String videoUrl;
    @Column(name = "category_code", length = 50) private String category;
    @Column(length = 100) private String region;
    @Column(name = "career_years") private Short careerYears;
    @Column(name = "certification_status", nullable = false, length = 20) private String certificationStatus = "APPROVED";
    @Column(name = "popularity_score", nullable = false, precision = 10, scale = 2) private BigDecimal popularityScore = BigDecimal.ZERO;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();

    public ArtisanProfile(SellerApplication application) {
        id = application.getMemberId();
        businessName = application.getBusinessName();
        introduction = application.getIntroduction();
    }

    public void updateBasics(Optional<String> businessName, Optional<String> introduction, Optional<String> imageUrl,
                             Optional<String> category, Optional<String> region, Optional<Short> careerYears) {
        businessName.ifPresent(value -> this.businessName = value);
        introduction.ifPresent(value -> this.introduction = value);
        imageUrl.ifPresent(value -> profileImageUrl = value);
        category.ifPresent(value -> this.category = value);
        region.ifPresent(value -> this.region = value);
        careerYears.ifPresent(value -> this.careerYears = value);
        updatedAt = LocalDateTime.now();
    }

    public void updateBiography(Optional<Short> certifiedYear, Optional<String> lineage, Optional<String> quote,
                                Optional<String> bio, Optional<String> videoUrl) {
        certifiedYear.ifPresent(value -> this.certifiedYear = value);
        lineage.ifPresent(value -> this.lineage = value);
        quote.ifPresent(value -> this.quote = value);
        bio.ifPresent(value -> this.bio = value);
        videoUrl.ifPresent(value -> this.videoUrl = value);
        updatedAt = LocalDateTime.now();
    }
}
