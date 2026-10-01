package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.global.security.JwtTokenProvider;
import com.jangingmall.backend.member.domain.Member;
import com.jangingmall.backend.member.domain.MemberRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.domain.MemberSocialAccountRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 로그인 → 토큰 갱신 → 로그아웃으로 이어지는 흐름을 실제 JWT와 인메모리 저장소로 검증한다.
 * AI 상세페이지 생성처럼 30분 넘게 걸리는 화면에서 액세스 토큰이 만료돼도 로그인이 유지돼야 한다.
 */
@ExtendWith(MockitoExtension.class)
class MemberRefreshTokenScenarioTest {

    private static final String SECRET = "local-dev-secret-key-minimum-256-bits-for-hs256-algorithm";
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, 1_800_000, 604_800_000, false);

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MemberSocialAccountRepository socialAccounts;
    @Mock
    private LoginAttemptService loginAttempts;

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(PROPERTIES);
    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore();
    private MemberAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new MemberAuthenticationService(
            memberRepository, passwordEncoder, jwtTokenProvider, PROPERTIES, store, socialAccounts, loginAttempts);
        Member member = activeMember();
        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        lenient().when(memberRepository.findByEmail("artisan@example.com")).thenReturn(Optional.of(member));
        lenient().when(passwordEncoder.matches(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("1. 로그인으로 받은 Refresh Token으로 갱신하면 해당 회원의 새 Access Token이 발급된다")
    void refreshRightAfterLogin() {
        MemberSession login = service.login("artisan@example.com", "password");

        MemberSession refreshed = service.refresh(login.refreshToken());

        assertThat(jwtTokenProvider.parseAccessToken(refreshed.accessToken()).memberId()).isEqualTo(1L);
        assertThat(refreshed.memberId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("2. 같은 Refresh Token으로 여러 요청이 동시에 갱신해도 모두 성공한다 (동시 갱신 401 방지)")
    void concurrentRefreshesAllSucceed() throws Exception {
        String refreshToken = service.login("artisan@example.com", "password").refreshToken();
        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MemberSession>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<MemberSession> task = () -> {
                    ready.countDown();
                    go.await(5, TimeUnit.SECONDS);
                    return service.refresh(refreshToken);
                };
                results.add(pool.submit(task));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<MemberSession> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS).accessToken()).isNotBlank();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("3. 갱신을 반복해도 같은 Refresh Token이 만료될 때까지 계속 유효하고 서버 저장값은 바뀌지 않는다")
    void refreshTokenStaysValidAcrossRefreshes() {
        String refreshToken = service.login("artisan@example.com", "password").refreshToken();
        String storedBefore = store.stored(1L);

        for (int i = 0; i < 5; i++) {
            assertThat(service.refresh(refreshToken).refreshToken()).isEqualTo(refreshToken);
        }

        assertThat(store.stored(1L)).isEqualTo(storedBefore);
    }

    @Test
    @DisplayName("4. 같은 계정으로 다시 로그인하면 이전 Refresh Token은 거절되고 새 토큰만 쓸 수 있다")
    void reLoginInvalidatesPreviousRefreshToken() {
        String first = service.login("artisan@example.com", "password").refreshToken();
        String second = service.login("artisan@example.com", "password").refreshToken();

        assertThat(second).isNotEqualTo(first);
        assertUnauthorized(() -> service.refresh(first));
        assertThat(service.refresh(second).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("5. 로그아웃하면 그 Refresh Token으로 더 이상 갱신할 수 없다")
    void logoutInvalidatesRefreshToken() {
        String refreshToken = service.login("artisan@example.com", "password").refreshToken();

        service.logout(1L);

        assertUnauthorized(() -> service.refresh(refreshToken));
    }

    @Test
    @DisplayName("6. 서버 저장소가 비어 있어도(재시작 등) 401로 끝나고, 다시 로그인하면 정상 갱신된다")
    void storeLossRequiresReLoginOnly() {
        String refreshToken = service.login("artisan@example.com", "password").refreshToken();
        store.clear();

        assertUnauthorized(() -> service.refresh(refreshToken));

        String reLoggedIn = service.login("artisan@example.com", "password").refreshToken();
        assertThat(service.refresh(reLoggedIn).accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("7. 탈퇴한 회원과 존재하지 않는 회원은 갱신할 수 없다")
    void ineligibleMembersCannotRefresh() {
        String token = service.login("artisan@example.com", "password").refreshToken();

        Member withdrawn = member();
        withdrawn.withdraw("탈퇴");
        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.of(withdrawn));
        assertUnauthorized(() -> service.refresh(token));

        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertUnauthorized(() -> service.refresh(token));
    }

    @Test
    @DisplayName("8. Access Token·만료된 토큰·위조된 토큰은 Refresh Token으로 인정되지 않는다")
    void invalidJwtsAreRejected() {
        String access = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);
        JwtTokenProvider expiredIssuer = new JwtTokenProvider(new JwtProperties(SECRET, 1_800_000, -1_000, false));
        String expiredRefresh = expiredIssuer.createRefreshToken(1L, MemberRole.USER);
        String real = service.login("artisan@example.com", "password").refreshToken();
        // 서명 마지막 글자는 패딩 비트 때문에 바꿔도 같은 값일 수 있어, 페이로드 중간 글자를 바꿔 확실히 위조한다.
        int index = real.indexOf('.') + 5;
        String tampered = real.substring(0, index) + (real.charAt(index) == 'A' ? 'B' : 'A') + real.substring(index + 1);

        assertUnauthorized(() -> service.refresh(access));
        assertUnauthorized(() -> service.refresh(expiredRefresh));
        assertUnauthorized(() -> service.refresh(tampered));
        assertUnauthorized(() -> service.refresh("not-a-jwt"));
    }

    private void assertUnauthorized(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(DomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private Member activeMember() {
        return member();
    }

    private Member member() {
        Member member = Member.register(
            "artisan@example.com", "hashed-password", "김도공", "01012345678",
            MemberRole.USER, true, true, true, false);
        ReflectionTestUtils.setField(member, "id", 1L);
        return member;
    }

    /** Redis 대신 쓰는 저장소. 회원당 토큰 1개(마지막 로그인 값)만 보관한다. */
    private static final class InMemoryRefreshTokenStore implements RefreshTokenStore {
        private final Map<Long, String> tokens = new ConcurrentHashMap<>();

        @Override
        public void save(Long memberId, String refreshToken, Duration ttl) {
            tokens.put(memberId, refreshToken);
        }

        @Override
        public boolean matches(Long memberId, String refreshToken) {
            return refreshToken.equals(tokens.get(memberId));
        }

        @Override
        public void delete(Long memberId) {
            tokens.remove(memberId);
        }

        String stored(Long memberId) {
            return tokens.get(memberId);
        }

        void clear() {
            tokens.clear();
        }
    }
}
