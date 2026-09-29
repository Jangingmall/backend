package com.jangingmall.backend.global.config;

import com.jangingmall.backend.member.application.SellerApplicationService;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.SellerApplication;
import com.jangingmall.backend.member.domain.SellerApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Profile("prod")
@Component
@RequiredArgsConstructor
public class DastAccountInitializer implements ApplicationRunner {

    private static final String DAST_LICENSE_URL = "https://midam.store/static/dast-placeholder.jpg";

    private final MemberRepository memberRepository;
    private final SellerApplicationRepository sellerApplicationRepository;
    private final SellerApplicationService sellerApplicationService;
    private final PasswordEncoder passwordEncoder;

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

        initUser(userEmail, userPassword);
        initArtisan(artisanEmail, artisanPassword, adminId);
    }

    private void initUser(String email, String password) {
        if (memberRepository.existsByEmail(email)) {
            log.info("[DAST] stgUser({}) 이미 존재 — 건너뜀", email);
            return;
        }
        Member user = Member.register(email, passwordEncoder.encode(password),
            "DAST-USER", "01000000001", MemberRole.USER, true, true, true, false);
        memberRepository.save(user);
        log.info("[DAST] stgUser({}) 생성 완료", email);
    }

    private void initArtisan(String email, String password, Long adminId) {
        if (memberRepository.existsByEmail(email)) {
            log.info("[DAST] stgArtisan({}) 이미 존재 — 건너뜀", email);
            return;
        }
        Member artisan = Member.register(email, passwordEncoder.encode(password),
            "DAST-ARTISAN", "01000000002", MemberRole.USER, true, true, true, false);
        Member saved = memberRepository.save(artisan);

        SellerApplication application = sellerApplicationRepository.save(
            new SellerApplication(saved.getId(), "DAST 테스트 공방", "DAST 자동화 테스트용 계정", DAST_LICENSE_URL)
        );
        sellerApplicationService.approve(adminId, application.getId());
        log.info("[DAST] stgArtisan({}) 생성 및 ARTISAN 승인 완료", email);
    }
}
