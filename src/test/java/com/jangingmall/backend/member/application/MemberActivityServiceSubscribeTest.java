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

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberActivityServiceSubscribeTest {

    @Mock private MemberAccess access;
    @Mock private MemberActivityRepository activities;
    @Mock private MemberReadRepository reads;

    private MemberActivityService service;

    @BeforeEach
    void setUp() {
        service = new MemberActivityService(access, activities, reads);
    }

    @Test
    @DisplayName("구독 — USER 역할이 아니면 FORBIDDEN 예외가 발생한다")
    void subscribeForbidden() {
        doThrow(new DomainException(ErrorCode.FORBIDDEN))
            .when(access).lockRole(eq(1L), eq(MemberRole.USER));

        assertThatThrownBy(() -> service.subscribe(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("구독 — 존재하지 않는 장인을 구독하면 NOT_FOUND 예외가 발생한다")
    void subscribeArtisanNotFound() {
        when(reads.artisan(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("구독 — 장인이 존재하면 구독이 저장된다")
    void subscribe() {
        when(reads.artisan(10L)).thenReturn(Optional.of(Map.of("artisanId", 10L)));

        service.subscribe(1L, 10L);

        verify(access).lockRole(1L, MemberRole.USER);
        verify(activities).subscribe(1L, 10L);
    }

    @Test
    @DisplayName("구독 취소 — USER 역할이 아니면 FORBIDDEN 예외가 발생한다")
    void unsubscribeForbidden() {
        doThrow(new DomainException(ErrorCode.FORBIDDEN))
            .when(access).lockRole(eq(1L), eq(MemberRole.USER));

        assertThatThrownBy(() -> service.unsubscribe(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("구독 취소 — 구독하지 않은 장인을 취소해도 예외가 발생하지 않는다")
    void unsubscribeIdempotent() {
        service.unsubscribe(1L, 999L);

        verify(access).lockRole(1L, MemberRole.USER);
        verify(activities).unsubscribe(1L, 999L);
    }

    @Test
    @DisplayName("알림 설정 — USER 역할이 아니면 FORBIDDEN 예외가 발생한다")
    void notificationsForbidden() {
        doThrow(new DomainException(ErrorCode.FORBIDDEN))
            .when(access).lockRole(eq(1L), eq(MemberRole.USER));

        assertThatThrownBy(() -> service.notifications(1L, true))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("알림 설정 — 알림 활성화 시 repository에 true로 전달된다")
    void notificationsEnable() {
        service.notifications(1L, true);

        verify(access).lockRole(1L, MemberRole.USER);
        verify(activities).notifications(1L, true);
    }

    @Test
    @DisplayName("알림 설정 — 알림 비활성화 시 repository에 false로 전달된다")
    void notificationsDisable() {
        service.notifications(1L, false);

        verify(activities).notifications(1L, false);
    }

    @Test
    @DisplayName("구독 목록 — USER 역할이 아니면 FORBIDDEN 예외가 발생한다")
    void subscriptionsForbidden() {
        doThrow(new DomainException(ErrorCode.FORBIDDEN))
            .when(access).requireRole(eq(1L), eq(MemberRole.USER));

        assertThatThrownBy(() -> service.subscriptions(1L, PageRequest.from(null, 20)))
            .isInstanceOfSatisfying(DomainException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("구독 목록 — reads에 페이지 요청을 위임한다")
    void subscriptions() {
        PageRequest page = PageRequest.from(null, 20);
        CursorPage<Map<String, Object>> expected = mock(CursorPage.class);
        when(reads.subscriptions(1L, page)).thenReturn(expected);

        CursorPage<Map<String, Object>> result = service.subscriptions(1L, page);

        verify(access).requireRole(1L, MemberRole.USER);
        assertThat(result).isEqualTo(expected);
    }
}
