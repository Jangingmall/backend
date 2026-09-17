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

    @Column(name = "job_id", length = 200)
    private String jobId;

    @Column(name = "request_id", length = 200)
    private String requestId;

    @Column(name = "idempotency_key", length = 200)
    private String idempotencyKey;

    @Column(name = "status_url", length = 500)
    private String statusUrl;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "react_document", columnDefinition = "TEXT")
    private String reactDocument;

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

    public void markQueued(String jobId, String requestId, String idempotencyKey, String statusUrl) {
        this.jobId = jobId;
        this.requestId = requestId;
        this.idempotencyKey = idempotencyKey;
        this.statusUrl = statusUrl;
        this.status = GenerationStatus.QUEUED;
    }

    public void complete(String reactDocumentJson, String idempotencyKey) {
        this.status = GenerationStatus.COMPLETED;
        this.reactDocument = reactDocumentJson;
        this.idempotencyKey = idempotencyKey;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = GenerationStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
