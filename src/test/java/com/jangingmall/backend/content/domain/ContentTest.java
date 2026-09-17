package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentTest {

    private static final String REACT_DOCUMENT_JSON =
        "{\"schemaVersion\":\"2.0\",\"canvasWidth\":774,\"root\":[]}";

    @Test
    @DisplayName("create() — DRAFT 상태, 체크리스트 모두 false로 초기화된다")
    void create() {
        Content content = Content.create(10L);

        assertThat(content.getProductId()).isEqualTo(10L);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.isFactCheckConfirmed()).isFalse();
        assertThat(content.isPhotoMatchConfirmed()).isFalse();
        assertThat(content.isDisplayApprovalBadge()).isFalse();
        assertThat(content.getReactDocument()).isNull();
    }

    @Test
    @DisplayName("storeReactDocument() — JSON blob이 저장된다")
    void storeReactDocument() {
        Content content = Content.create(10L);

        content.storeReactDocument(REACT_DOCUMENT_JSON);

        assertThat(content.getReactDocument()).isEqualTo(REACT_DOCUMENT_JSON);
    }

    @Test
    @DisplayName("storeReactDocument() — 기존 blob을 덮어쓴다")
    void storeReactDocumentOverwrite() {
        Content content = Content.create(10L);
        content.storeReactDocument("{\"schemaVersion\":\"1.0\"}");

        content.storeReactDocument(REACT_DOCUMENT_JSON);

        assertThat(content.getReactDocument()).isEqualTo(REACT_DOCUMENT_JSON);
    }

    @Test
    @DisplayName("verifyOwnership() — artisanId가 다르면 ForbiddenException이 발생한다")
    void verifyOwnershipFailed() {
        Content content = Content.create(10L);

        assertThatThrownBy(() -> content.verifyOwnership(999L, 1L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("verifyOwnership() — artisanId가 같으면 예외가 발생하지 않는다")
    void verifyOwnershipSuccess() {
        Content content = Content.create(10L);
        content.verifyOwnership(1L, 1L);
    }

    @Test
    @DisplayName("submitForReview() — DRAFT에서 PENDING_REVIEW로 전이된다")
    void submitForReviewFromDraft() {
        Content content = Content.create(10L);

        content.submitForReview();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("submitForReview() — REJECTED에서 PENDING_REVIEW로 전이된다")
    void submitForReviewFromRejected() {
        Content content = Content.create(10L);
        content.submitForReview();
        content.reject();

        content.submitForReview();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("submitForReview() — PENDING_REVIEW 상태에서 호출하면 BusinessRuleViolationException이 발생한다")
    void submitForReviewInvalidTransition() {
        Content content = Content.create(10L);
        content.submitForReview();

        assertThatThrownBy(content::submitForReview)
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("approve() — PENDING_REVIEW에서 APPROVED로 전이되고 체크리스트가 반영된다")
    void approve() {
        Content content = Content.create(10L);
        content.submitForReview();

        content.approve(true, true, false);

        assertThat(content.getStatus()).isEqualTo(ContentStatus.APPROVED);
        assertThat(content.isFactCheckConfirmed()).isTrue();
        assertThat(content.isPhotoMatchConfirmed()).isTrue();
        assertThat(content.isDisplayApprovalBadge()).isFalse();
    }

    @Test
    @DisplayName("approve() — DRAFT 상태에서 호출하면 BusinessRuleViolationException이 발생한다")
    void approveInvalidTransition() {
        Content content = Content.create(10L);

        assertThatThrownBy(() -> content.approve(true, true, true))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("reject() — PENDING_REVIEW에서 REJECTED로 전이된다")
    void reject() {
        Content content = Content.create(10L);
        content.submitForReview();

        content.reject();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.REJECTED);
    }

    @Test
    @DisplayName("reject() — DRAFT 상태에서 호출하면 BusinessRuleViolationException이 발생한다")
    void rejectInvalidTransition() {
        Content content = Content.create(10L);

        assertThatThrownBy(content::reject)
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("publish() — APPROVED에서 PUBLISHED로 전이된다")
    void publish() {
        Content content = Content.create(10L);
        content.submitForReview();
        content.approve(true, true, true);

        content.publish();

        assertThat(content.getStatus()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    @DisplayName("publish() — APPROVED가 아닌 상태에서 호출하면 BusinessRuleViolationException이 발생한다")
    void publishInvalidTransition() {
        Content content = Content.create(10L);
        content.submitForReview();

        assertThatThrownBy(content::publish)
            .isInstanceOf(BusinessRuleViolationException.class);
    }
}
