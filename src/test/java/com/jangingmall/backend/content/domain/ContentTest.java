package com.jangingmall.backend.content.domain;

import com.jangingmall.backend.global.exception.BusinessRuleViolationException;
import com.jangingmall.backend.global.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentTest {

    @Test
    @DisplayName("create() — DRAFT 상태, 체크리스트 모두 false로 초기화된다")
    void create() {
        Content content = Content.create(10L);

        assertThat(content.getProductId()).isEqualTo(10L);
        assertThat(content.getStatus()).isEqualTo(ContentStatus.DRAFT);
        assertThat(content.isFactCheckConfirmed()).isFalse();
        assertThat(content.isPhotoMatchConfirmed()).isFalse();
        assertThat(content.isDisplayApprovalBadge()).isFalse();
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
    @DisplayName("replaceBlocks() — 기존 블록을 새 목록으로 교체한다")
    void replaceBlocks() {
        Content content = Content.create(10L);
        ReflectionTestUtils.setField(content, "id", 1L);
        ContentBlock block = ContentBlock.create(content, (short) 1, BlockTag.h2, null, null, "소제목");

        content.replaceBlocks(List.of(block));

        assertThat(content.getBlocks()).hasSize(1);
        assertThat(content.getBlocks().get(0).getTag()).isEqualTo(BlockTag.h2);
    }

    @Test
    @DisplayName("touchVersion() — 버전 번호가 1 증가한다")
    void touchVersion() {
        Content content = Content.create(10L);
        ReflectionTestUtils.setField(content, "version", 2);

        content.touchVersion();

        assertThat(content.getVersion()).isEqualTo(3);
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
