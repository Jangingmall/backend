package com.jangingmall.backend.global.config;

import com.jangingmall.backend.member.application.MemberService;
import com.jangingmall.backend.member.application.MemberSignupCommand;
import com.jangingmall.backend.member.application.MemberSignupResult;
import com.jangingmall.backend.member.application.SellerApplicationService;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class DastAccountInitializer implements ApplicationRunner {

    private static final String DAST_LICENSE_URL = "https://midam.store/static/dast-placeholder.jpg";

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final SellerApplicationService sellerApplicationService;

    @Value("${dast.user.email:}")
    private String userEmail;

    @Value("${dast.user.password:}")
    private String userPassword;

    @Value("${dast.artisan.email:}")
    private String artisanEmail;

    @Value("${dast.artisan.password:}")
    private String artisanPassword;

    @Value("${dast.admin.email:}")
    private String adminEmail;

    @Value("${dast.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userEmail.isBlank() || artisanEmail.isBlank() || adminEmail.isBlank()) {
            log.info("[DAST] DAST_* 환경변수 미설정 — 계정 초기화 건너뜀");
            return;
        }

        Long adminId = memberRepository.findByEmail(adminEmail)
            .map(Member::getId)
            .orElse(null);
        if (adminId == null) {
            log.warn("[DAST] stgAdmin({}) 미존재 — V11 Migration 적용 여부 확인 필요", adminEmail);
            return;
        }

        initUser();
        initArtisan(adminId);
    }

    private void initUser() {
        if (memberRepository.existsByEmail(userEmail)) {
            log.info("[DAST] stgUser({}) 이미 존재 — 건너뜀", userEmail);
            return;
        }
        memberService.signUp(new MemberSignupCommand(
            userEmail, userPassword, userPassword,
            "DAST-USER", "01000000001", MemberRole.USER,
            true, true, true, false
        ));
        log.info("[DAST] stgUser({}) 생성 완료", userEmail);
    }

    private void initArtisan(Long adminId) {
        if (memberRepository.existsByEmail(artisanEmail)) {
            log.info("[DAST] stgArtisan({}) 이미 존재 — 건너뜀", artisanEmail);
            return;
        }
        MemberSignupResult result = memberService.signUp(new MemberSignupCommand(
            artisanEmail, artisanPassword, artisanPassword,
            "DAST-ARTISAN", "01000000002", MemberRole.USER,
            true, true, true, false
        ));
        var application = sellerApplicationService.apply(
            result.memberId(), "DAST 테스트 공방", "DAST 자동화 테스트용 계정", DAST_LICENSE_URL
        );
        sellerApplicationService.approve(adminId, application.applicationId());
        log.info("[DAST] stgArtisan({}) 생성 및 ARTISAN 승인 완료", artisanEmail);
    }
}
