package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.MemberRole;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {

    @Mock private MemberAccess access;
    @Mock private MemberReadRepository reads;
    private MemberQueryService service;

    @BeforeEach
    void setUp() {
        service = new MemberQueryService(access, reads);
    }

    @Test
    void readsOrderOnlyWithinAuthenticatedMembersScope() {
        Map<String, Object> expected = Map.of("orderId", 20L, "orderNumber", "ORD-20");
        when(reads.order(1L, 20L)).thenReturn(Optional.of(expected));

        assertThat(service.order(1L, 20L)).isEqualTo(expected);

        verify(access).requireRole(1L, MemberRole.USER);
        verify(reads).order(1L, 20L);
    }

    @Test
    void hidesAnotherMembersOrderAsNotFound() {
        when(reads.order(1L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.order(1L, 20L))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
