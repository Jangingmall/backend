package com.jangingmall.backend.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "content_generation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GenerationStatus status;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String images;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "how_made", columnDefinition = "TEXT", nullable = false)
    private String howMade;

    @Column(name = "care_tips", columnDefinition = "TEXT", nullable = false)
    private String careTips;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "generated_blocks", columnDefinition = "TEXT")
    private String generatedBlocks;

    public static ContentGeneration create(Long productId, String images, String productName, String howMade, String careTips) {
        ContentGeneration generation = new ContentGeneration();
        generation.productId = productId;
        generation.status = GenerationStatus.PROCESSING;
        generation.images = images;
        generation.productName = productName;
        generation.howMade = howMade;
        generation.careTips = careTips;
        generation.requestedAt = LocalDateTime.now();
        return generation;
    }

    public void complete(String generatedBlocks) {
        this.status = GenerationStatus.COMPLETED;
        this.generatedBlocks = generatedBlocks;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = GenerationStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
