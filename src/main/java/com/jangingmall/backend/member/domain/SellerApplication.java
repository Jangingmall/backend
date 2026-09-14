package com.jangingmall.backend.member.domain;

import com.jangingmall.backend.global.exception.ConflictException;
import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seller_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication {
    public enum Status { PENDING, APPROVED, REJECTED }
    public enum Stage { PENDING, IN_PROGRESS, COMPLETED, FAILED }
    public enum Step { DOCUMENT_REVIEW, CRAFTSMANSHIP_REVIEW, DIGITAL_CONVERSION, ORDER_SYSTEM_INTEGRATION }
    public enum Qualification { NATIONAL_INTANGIBLE_HERITAGE, MASTER_CRAFTSMAN, SENIOR_CRAFTSMAN, YOUNG_CRAFTSMAN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id") private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "business_name", nullable = false, length = 100) private String businessName;
    @Column(nullable = false, length = 255) private String introduction;
    @Column(name = "business_license_image_url", nullable = false, length = 500) private String businessLicenseImageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    @Version private int version;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();
    @Column(name = "rejection_reason", length = 65535) private String rejectionReason;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Enumerated(EnumType.STRING) @Column(name = "document_review", nullable = false) private Stage documentReview = Stage.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "craftsmanship_review", nullable = false) private Stage craftsmanshipReview = Stage.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "digital_conversion", nullable = false) private Stage digitalConversion = Stage.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "order_system_integration", nullable = false) private Stage orderSystemIntegration = Stage.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "qualification_tier", length = 40) private Qualification qualificationTier;

    public SellerApplication(Long memberId, String businessName, String introduction, String licenseUrl) {
        this.memberId = memberId;
        this.businessName = businessName;
        this.introduction = introduction;
        businessLicenseImageUrl = licenseUrl;
    }

    public void approve(Long adminId) {
        requirePending();
        status = Status.APPROVED;
        reviewedBy = adminId;
        reviewedAt = LocalDateTime.now();
        documentReview = Stage.COMPLETED;
        craftsmanshipReview = Stage.COMPLETED;
        digitalConversion = Stage.COMPLETED;
        orderSystemIntegration = Stage.COMPLETED;
    }

    public void reject(Long adminId, String reason) {
        requirePending();
        status = Status.REJECTED;
        rejectionReason = reason;
        reviewedBy = adminId;
        reviewedAt = LocalDateTime.now();
        documentReview = Stage.FAILED;
    }

    public boolean updatePipeline(Step step, Stage next, Qualification qualification) {
        requirePending();
        requireStageValue(next);
        requireStageTransition(step, next);
        requirePreviousStepCompleted(step);
        requireQualification(step, next, qualification);
        if (qualification != null) {
            qualificationTier = qualification;
        }
        updateStep(step, next);
        if (allStepsCompleted()) {
            status = Status.APPROVED;
            reviewedAt = LocalDateTime.now();
            return true;
        }
        return false;
    }

    public void assignReviewer(Long adminId) {
        reviewedBy = adminId;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new ConflictException(MemberErrorMessage.ALREADY_REVIEWED);
        }
    }

    private void requireStageValue(Stage next) {
        if (next == Stage.PENDING) {
            throw new BusinessRuleViolationException("심사 단계는 대기 상태로 변경할 수 없습니다");
        }
    }

    private void requireStageTransition(Step step, Stage next) {
        Stage current = stageOf(step);
        if (current == Stage.COMPLETED || current == Stage.FAILED) {
            throw new BusinessRuleViolationException("완료되었거나 실패한 심사 단계는 변경할 수 없습니다");
        }
        if (current == Stage.IN_PROGRESS && next == Stage.IN_PROGRESS) {
            throw new BusinessRuleViolationException("이미 진행 중인 심사 단계입니다");
        }
    }

    private void requirePreviousStepCompleted(Step step) {
        boolean valid = switch (step) {
            case DOCUMENT_REVIEW -> true;
            case CRAFTSMANSHIP_REVIEW -> documentReview == Stage.COMPLETED;
            case DIGITAL_CONVERSION -> craftsmanshipReview == Stage.COMPLETED;
            case ORDER_SYSTEM_INTEGRATION -> digitalConversion == Stage.COMPLETED;
        };
        if (!valid) {
            throw new BusinessRuleViolationException("이전 심사 단계를 완료해야 합니다");
        }
    }

    private void requireQualification(Step step, Stage next, Qualification qualification) {
        if (step == Step.CRAFTSMANSHIP_REVIEW && next == Stage.COMPLETED && qualification == null) {
            throw new BusinessRuleViolationException("수공정성 심사 완료에는 자격 등급이 필요합니다");
        }
    }

    private Stage stageOf(Step step) {
        return switch (step) {
            case DOCUMENT_REVIEW -> documentReview;
            case CRAFTSMANSHIP_REVIEW -> craftsmanshipReview;
            case DIGITAL_CONVERSION -> digitalConversion;
            case ORDER_SYSTEM_INTEGRATION -> orderSystemIntegration;
        };
    }

    private void updateStep(Step step, Stage next) {
        switch (step) {
            case DOCUMENT_REVIEW -> documentReview = next;
            case CRAFTSMANSHIP_REVIEW -> craftsmanshipReview = next;
            case DIGITAL_CONVERSION -> digitalConversion = next;
            case ORDER_SYSTEM_INTEGRATION -> orderSystemIntegration = next;
        }
    }

    private boolean allStepsCompleted() {
        return documentReview == Stage.COMPLETED && craftsmanshipReview == Stage.COMPLETED
            && digitalConversion == Stage.COMPLETED && orderSystemIntegration == Stage.COMPLETED;
    }
}
