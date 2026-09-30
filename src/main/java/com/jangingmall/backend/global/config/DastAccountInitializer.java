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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Profile({"prod", "local-postgresql"})
@Component
@RequiredArgsConstructor
public class DastAccountInitializer implements ApplicationRunner {

    private static final String DAST_LICENSE_URL = "https://midam.store/static/dast-placeholder.jpg";

    private final MemberRepository memberRepository;
    private final MemberService memberService;
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

    @Value("${dast.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // DAST 이메일 정규화 (trim + toLowerCase) - 프로젝트 전체 이메일 정책과 일치
        String normalizedUserEmail = normalizeEmail(userEmail);
        String normalizedArtisanEmail = normalizeEmail(artisanEmail);
        String normalizedAdminEmail = normalizeEmail(adminEmail);

        if (normalizedUserEmail.isBlank() || normalizedArtisanEmail.isBlank() || normalizedAdminEmail.isBlank()) {
            log.info("[DAST] DAST_* 환경변수 미설정 — 계정 초기화 건너뜀");
            return;
        }

        Long adminId = initAdmin(normalizedAdminEmail);
        initUser(normalizedUserEmail);
        initArtisan(normalizedArtisanEmail, adminId);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Long initAdmin(String normalizedAdminEmail) {
        return memberRepository.findByEmail(normalizedAdminEmail)
            .map(existing -> {
                existing.changePassword(passwordEncoder.encode(adminPassword));
                existing.activate();
                memberRepository.save(existing);
                log.info("[DAST] stgAdmin({}) 비밀번호 재해싱 완료", normalizedAdminEmail);
                return existing.getId();
            })
            .orElseGet(() -> {
                Member admin = Member.register(
                    normalizedAdminEmail,
                    passwordEncoder.encode(adminPassword),
                    "DAST-ADMIN", "01000000003", MemberRole.ADMIN,
                    true, true, true, false
                );
                Long id = memberRepository.save(admin).getId();
                log.info("[DAST] stgAdmin({}) 생성 완료", normalizedAdminEmail);
                return id;
            });
    }

    private void initUser(String normalizedUserEmail) {
        if (memberRepository.existsByEmail(normalizedUserEmail)) {
            memberRepository.findByEmail(normalizedUserEmail).ifPresent(existing -> {
                existing.changePassword(passwordEncoder.encode(userPassword));
                existing.activate();
                memberRepository.save(existing);
                log.info("[DAST] stgUser({}) 비밀번호 재해싱 완료", normalizedUserEmail);
            });
            return;
        }
        MemberSignupResult signupResult = memberService.signUp(new MemberSignupCommand(
            normalizedUserEmail, userPassword, userPassword,
            "DAST-USER", "01000000001", MemberRole.USER,
            true, true, true, false
        ));
        memberRepository.findById(signupResult.memberId()).ifPresent(m -> {
            m.activate();
            memberRepository.save(m);
        });
        log.info("[DAST] stgUser({}) 생성 완료", normalizedUserEmail);
    }

    private void initArtisan(String normalizedArtisanEmail, Long adminId) {
        if (memberRepository.existsByEmail(normalizedArtisanEmail)) {
            memberRepository.findByEmail(normalizedArtisanEmail).ifPresent(existing -> {
                existing.changePassword(passwordEncoder.encode(artisanPassword));
                existing.activate();
                memberRepository.save(existing);
                log.info("[DAST] stgArtisan({}) 비밀번호 재해싱 완료", normalizedArtisanEmail);
            });
            return;
        }
        MemberSignupResult result = memberService.signUp(new MemberSignupCommand(
            normalizedArtisanEmail, artisanPassword, artisanPassword,
            "DAST-ARTISAN", "01000000002", MemberRole.USER,
            true, true, true, false
        ));
        memberRepository.findById(result.memberId()).ifPresent(m -> {
            m.activate();
            memberRepository.save(m);
        });
        var application = sellerApplicationService.apply(
            result.memberId(), "DAST 테스트 공방", "DAST 자동화 테스트용 계정", DAST_LICENSE_URL
        );
        sellerApplicationService.approve(adminId, application.applicationId());
        log.info("[DAST] stgArtisan({}) 생성 및 ARTISAN 승인 완료", normalizedArtisanEmail);
    }
}
