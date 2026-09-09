package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.ArtisanProfile;
import com.jangingmall.backend.member.domain.ArtisanProfileRepository;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.SellerApplication;
import com.jangingmall.backend.member.domain.SellerApplicationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerApplicationServiceTest {

    @Mock private MemberAccess access;
    @Mock private SellerApplicationRepository applications;
    @Mock private ArtisanProfileRepository artisans;
    @Mock private MemberReadRepository reads;
    private SellerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SellerApplicationService(access, applications, artisans, reads);
    }

    @Test
    @DisplayName("심사 대기 신청이 있으면 장인 입점 신청을 중복 생성하지 않는다")
    void rejectsDuplicatePendingApplication() {
        when(applications.existsByMemberIdAndStatus(1L, SellerApplication.Status.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.apply(1L, "도공방", "청자를 빚습니다", "https://cdn.example/license.webp"))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    @DisplayName("관리자 승인 시 신청 상태와 회원 역할, 장인 프로필을 함께 만든다")
    void approvesApplicationAndPromotesMember() {
        SellerApplication application = application();
        Member member = activeMember();
        when(applications.findById(20L)).thenReturn(Optional.of(application));
        when(access.lock(1L)).thenReturn(member);

        SellerApplicationData result = service.approve(99L, 20L);

        assertThat(result.status()).isEqualTo(SellerApplication.Status.APPROVED);
        assertThat(result.pipeline().orderSystemIntegration()).isEqualTo(SellerApplication.Stage.COMPLETED);
        assertThat(member.getRole()).isEqualTo(MemberRole.ARTISAN);
        ArgumentCaptor<ArtisanProfile> profile = ArgumentCaptor.forClass(ArtisanProfile.class);
        verify(artisans).save(profile.capture());
        assertThat(profile.getValue().getId()).isEqualTo(1L);
        verify(applications).flush();
    }

    @Test
    @DisplayName("파이프라인은 이전 단계를 완료하기 전에는 다음 단계로 진행할 수 없다")
    void requiresPipelineOrder() {
        SellerApplication application = application();
        when(applications.findById(20L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.updatePipeline(99L, 20L, SellerApplication.Step.CRAFTSMANSHIP_REVIEW,
            SellerApplication.Stage.IN_PROGRESS, null)).hasMessageContaining("이전 심사 단계");
    }

    @Test
    @DisplayName("완료한 심사 단계는 이전 상태로 되돌릴 수 없다")
    void preventsPipelineRegression() {
        SellerApplication application = application();
        when(applications.findById(20L)).thenReturn(Optional.of(application));

        service.updatePipeline(99L, 20L, SellerApplication.Step.DOCUMENT_REVIEW, SellerApplication.Stage.COMPLETED, null);

        assertThatThrownBy(() -> service.updatePipeline(99L, 20L, SellerApplication.Step.DOCUMENT_REVIEW,
            SellerApplication.Stage.IN_PROGRESS, null)).hasMessageContaining("변경할 수 없습니다");
    }

    @Test
    @DisplayName("네 심사 단계가 완료되면 자격 등급과 함께 회원을 장인으로 전환한다")
    void promotesAfterLastPipelineStep() {
        SellerApplication application = application();
        Member member = activeMember();
        when(applications.findById(20L)).thenReturn(Optional.of(application));
        when(access.lock(1L)).thenReturn(member);

        service.updatePipeline(99L, 20L, SellerApplication.Step.DOCUMENT_REVIEW, SellerApplication.Stage.COMPLETED, null);
        service.updatePipeline(99L, 20L, SellerApplication.Step.CRAFTSMANSHIP_REVIEW, SellerApplication.Stage.COMPLETED,
            SellerApplication.Qualification.MASTER_CRAFTSMAN);
        service.updatePipeline(99L, 20L, SellerApplication.Step.DIGITAL_CONVERSION, SellerApplication.Stage.COMPLETED, null);
        SellerApplicationPipelineResult result = service.updatePipeline(99L, 20L, SellerApplication.Step.ORDER_SYSTEM_INTEGRATION,
            SellerApplication.Stage.COMPLETED, null);

        assertThat(result.overallStatus()).isEqualTo(SellerApplication.Status.APPROVED);
        assertThat(result.qualificationTier()).isEqualTo(SellerApplication.Qualification.MASTER_CRAFTSMAN);
        assertThat(member.getRole()).isEqualTo(MemberRole.ARTISAN);
        verify(artisans).save(any(ArtisanProfile.class));
    }

    private SellerApplication application() {
        SellerApplication application = new SellerApplication(1L, "도공방", "청자를 빚습니다", "https://cdn.example/license.webp");
        ReflectionTestUtils.setField(application, "id", 20L);
        return application;
    }

    private Member activeMember() {
        Member member = Member.register("artisan@example.com", "hash", "김도공", "01012345678", MemberRole.USER,
            true, true, true, false);
        member.activate();
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }
}
