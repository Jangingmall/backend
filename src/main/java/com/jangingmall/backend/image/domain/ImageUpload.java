package com.jangingmall.backend.image.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "image_upload")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageUpload {

    @Id
    @Column(name = "image_id", length = 30)
    private String id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImagePurpose purpose;

    @Column(name = "source_width", nullable = false)
    private int sourceWidth;

    @Column(name = "source_height", nullable = false)
    private int sourceHeight;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String variants;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public ImageUpload(String id, Long memberId, ImagePurpose purpose, int sourceWidth, int sourceHeight,
                       String variants, Instant expiresAt) {
        this.id = id;
        this.memberId = memberId;
        this.purpose = purpose;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
        this.variants = variants;
        this.consumed = false;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /**
     * 벌크 update(consumeIfOwnedAndActive)로 DB 가 소비 처리된 뒤, 같은 트랜잭션의 영속성 컨텍스트에 남은 이 엔티티도 맞춘다.
     * 맞추지 않으면 같은 트랜잭션에서 곧바로 공개 variant 를 만들 때 "아직 소비되지 않음"으로 보여 409 가 난다.
     */
    public void markConsumed() {
        this.consumed = true;
    }
}
