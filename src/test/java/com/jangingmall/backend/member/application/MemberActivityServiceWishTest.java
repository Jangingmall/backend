package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.MemberActivityRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberActivityServiceWishTest {

    @Mock private MemberAccess access;
    @Mock private MemberActivityRepository activities;
    @Mock private MemberReadRepository reads;

    private MemberActivityService service;

    @BeforeEach
    void setUp() {
        service = new MemberActivityService(access, activities, reads);
    }

    @Test
    @DisplayName("찜 등록 — 유저가 상품을 찜하면 wishlist에 저장된다")
    void wish() {
        when(reads.productVisible(10L)).thenReturn(true);

        service.wish(1L, 10L);

        verify(access).lockRole(1L, MemberRole.USER);
        verify(activities).wish(1L, 10L);
    }

    @Test
    @DisplayName("찜 등록 — 존재하지 않는 상품을 찜하면 NOT_FOUND 예외가 발생한다")
    void wishProductNotFound() {
        when(reads.productVisible(10L)).thenReturn(false);

        assertThatThrownBy(() -> service.wish(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> org.assertj.core.api.Assertions.assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("찜 등록 — USER 역할이 아니면 FORBIDDEN 예외가 발생한다")
    void wishForbidden() {
        doThrow(new DomainException(ErrorCode.FORBIDDEN))
            .when(access).lockRole(eq(1L), eq(MemberRole.USER));

        assertThatThrownBy(() -> service.wish(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> org.assertj.core.api.Assertions.assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("찜 취소 — 유저가 찜을 취소하면 wishlist에서 삭제된다")
    void unwish() {
        service.unwish(1L, 10L);

        verify(access).lockRole(1L, MemberRole.USER);
        verify(activities).unwish(1L, 10L);
    }

    @Test
    @DisplayName("찜 취소 — 찜하지 않은 상품을 취소해도 예외가 발생하지 않는다")
    void unwishIdempotent() {
        service.unwish(1L, 999L);

        verify(activities).unwish(1L, 999L);
    }
}
