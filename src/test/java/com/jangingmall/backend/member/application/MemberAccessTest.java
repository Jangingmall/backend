package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class MemberAccessTest {

    private final MemberRepository members = Mockito.mock(MemberRepository.class);
    private final MemberAccess access = new MemberAccess(members);

    @Test
    void artisanCanUseUserPermission() {
        Member artisan = active(MemberRole.ARTISAN);
        when(members.findById(1L)).thenReturn(Optional.of(artisan));

        assertThat(access.requireRole(1L, MemberRole.USER)).isSameAs(artisan);
    }

    @Test
    void artisanCannotSubmitAnotherApplication() {
        Member artisan = active(MemberRole.ARTISAN);
        when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(artisan));

        assertThatThrownBy(() -> access.lockExactRole(1L, MemberRole.USER))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    private Member active(MemberRole role) {
        Member member = Member.register("artisan@example.com", "hash", "김도공", "01012345678", role,
            true, true, true, false);
        member.activate();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
