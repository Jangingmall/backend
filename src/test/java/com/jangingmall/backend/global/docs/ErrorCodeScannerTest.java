package com.jangingmall.backend.global.docs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * ErrorCodeScanner 단위 검증.
 *
 * 호출 체인 (depth 5):
 *   NotificationController (depth-1)
 *     → NotificationService (depth-2)
 *       → NotificationService#findOrThrow (depth-3) → NotFoundException
 *       → Notification#validateOwnership (depth-4) → ForbiddenException
 *       → Notification#markAsRead / delete (depth-4) → BusinessRuleViolationException
 *         (→ MethodRef 재귀 depth-5 한도)
 */
class ErrorCodeScannerTest {

    private static Map<String, Set<String>> scanResult;

    @BeforeAll
    static void scan() throws IOException {
        Path classesRoot = Paths.get("build/classes/java/main");
        ErrorCodeScanner scanner = new ErrorCodeScanner(classesRoot);
        scanResult = scanner.scan("com.jangingmall.backend.notification");
        System.out.println("=== 스캔 결과 ===");
        scanResult.forEach((endpoint, codes) ->
            System.out.printf("  %-50s → %s%n", endpoint, codes));
    }

    @Test
    @DisplayName("GET /api/notifications/{notificationId} — NotFoundException, ForbiddenException 탐지")
    void getNotification_detectsNotFoundAndForbidden() {
        Set<String> codes = findEndpoint("GET /api/notifications/{notificationId}");

        assertThat(codes)
            .as("단건 조회: NOT_FOUND, FORBIDDEN이 탐지되어야 한다")
            .containsExactlyInAnyOrder("NOT_FOUND", "FORBIDDEN");
    }

    @Test
    @DisplayName("PATCH /api/notifications/{notificationId}/read — NotFoundException, ForbiddenException, BusinessRuleViolationException 탐지")
    void markAsRead_detectsThreeErrorCodes() {
        Set<String> codes = findEndpoint("PATCH /api/notifications/{notificationId}/read");

        assertThat(codes)
            .as("읽음 처리: NOT_FOUND, FORBIDDEN, BUSINESS_RULE_VIOLATION이 탐지되어야 한다")
            .containsExactlyInAnyOrder("NOT_FOUND", "FORBIDDEN", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("DELETE /api/notifications/{notificationId} — NotFoundException, ForbiddenException, BusinessRuleViolationException 탐지")
    void deleteNotification_detectsThreeErrorCodes() {
        Set<String> codes = findEndpoint("DELETE /api/notifications/{notificationId}");

        assertThat(codes)
            .as("삭제: NOT_FOUND, FORBIDDEN, BUSINESS_RULE_VIOLATION이 탐지되어야 한다")
            .containsExactlyInAnyOrder("NOT_FOUND", "FORBIDDEN", "BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("GET /api/notifications — 에러코드 없음 (빈 리스트 반환)")
    void listNotifications_noErrorCodes() {
        assertThat(scanResult)
            .as("목록 조회 엔드포인트는 스캔 결과에 없어야 한다 (에러 없음)")
            .doesNotContainKey("GET /api/notifications");
    }

    @Test
    @DisplayName("스캐너가 탐지한 총 에러코드 종류 — 3가지 이상")
    void scan_detectsAtLeastThreeDistinctErrorCodes() {
        Set<String> allCodes = scanResult.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toSet());

        assertThat(allCodes)
            .as("전체 탐지된 에러코드는 최소 3가지여야 한다")
            .hasSizeGreaterThanOrEqualTo(3)
            .contains("NOT_FOUND", "FORBIDDEN", "BUSINESS_RULE_VIOLATION");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빌드 게이트: TODO-DEDUP 마커 잔존 검사
    //
    // 워크플로:
    //   1. 중복 제거 진행 중인 파일에 마커 추가
    //      형식: TODO + "-DEDUP: [이유] RestDocsControllerTest.ERROR_RESPONSE_FIELDS로 교체 예정"
    //   2. 중복 제거 완료 후 마커 제거
    //   3. 이 테스트가 마커 잔존을 빌드 게이트로 강제 — 사람 기억에 의존하지 않음
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("TODO-DEDUP 마커가 소스에 남아 있으면 빌드 실패 (하드코딩 경고 제거 게이트)")
    void noDedupMarkersRemaining_buildGate() throws IOException {
        Path srcRoot = Paths.get("src/test/java");
        List<String> violations = new ArrayList<>();

        Files.walkFileTree(srcRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                // 게이트 파일 자체 제외 (자기 참조 탐지 방지)
                if (file.toString().endsWith("ErrorCodeScannerTest.java")) return FileVisitResult.CONTINUE;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).contains("TODO" + "-DEDUP")) {
                        violations.add(srcRoot.relativize(file) + ":" + (i + 1));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertThat(violations)
            .as("TODO-DEDUP 마커가 남아 있는 파일 — 중복 제거 후 반드시 제거하세요:\n%s",
                String.join("\n", violations))
            .isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빌드 게이트: WriteTxDetector 양성 탐지 검증
    //
    // NotificationService.markAsRead 는 @Transactional (write-tx) 선언.
    // WriteTxDetector 가 올바르게 탐지하는지 검증한다.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("WriteTxDetector — NotificationService.markAsRead 는 write-tx 로 탐지되어야 한다 (양성 픽스처)")
    void writeTxDetector_detectsMarkAsRead() throws IOException {
        Path classesRoot = Paths.get("build/classes/java/main");
        WriteTxDetector detector = new WriteTxDetector(classesRoot);

        // markAsRead(Long, Long) 의 JVM descriptor
        String desc = "(Ljava/lang/Long;Ljava/lang/Long;)V";
        boolean isWrite = detector.isWritePath(
            "com/jangingmall/backend/notification/application/NotificationService",
            "markAsRead",
            desc,
            0
        );

        assertThat(isWrite)
            .as("@Transactional(write) 메서드는 write-tx 경로로 탐지되어야 한다")
            .isTrue();
    }

    @Test
    @DisplayName("WriteTxDetector — NotificationService.findAll 은 readOnly-tx 로 탐지 제외되어야 한다 (음성 픽스처)")
    void writeTxDetector_excludesReadOnlyTx() throws IOException {
        Path classesRoot = Paths.get("build/classes/java/main");
        WriteTxDetector detector = new WriteTxDetector(classesRoot);

        String desc = "(Ljava/lang/Long;)Ljava/util/List;";
        boolean isWrite = detector.isWritePath(
            "com/jangingmall/backend/notification/application/NotificationService",
            "findAll",
            desc,
            0
        );

        assertThat(isWrite)
            .as("@Transactional(readOnly=true) 메서드는 write-tx 경로에서 제외되어야 한다")
            .isFalse();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빌드 게이트: 미커버 엔드포인트 검사
    //
    // 완전 탐지 조건(A-D) 미충족 AND @ApiErrorCodes 미선언 엔드포인트가 있으면 빌드 실패.
    // 전체 패키지를 스캔하므로 새 도메인이 추가될 때 자동으로 적용된다.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("미커버 엔드포인트 빌드 게이트 — write-tx 엔드포인트에 UNAUTHORIZED/@ApiErrorCodes 선언이 없으면 빌드 실패")
    void noUncoveredEndpoints_buildGate() throws IOException {
        Path classesRoot = Paths.get("build/classes/java/main");
        ErrorCodeScanner scanner = new ErrorCodeScanner(classesRoot);
        SecurityRuleDetector securityDetector = new SecurityRuleDetector();

        Map<String, Set<String>> asmResults = scanner.scanAsmOnly("com.jangingmall.backend");
        Map<String, Set<String>> annotationResults = scanner.scanAnnotationsOnly("com.jangingmall.backend");

        Set<String> endpoints = new LinkedHashSet<>(asmResults.keySet());
        endpoints.addAll(annotationResults.keySet());

        // 인증 필요 엔드포인트인데 UNAUTHORIZED가 자동 탐지도 안 되고 @ApiErrorCodes도 없는 케이스를 찾는다.
        // SecurityRuleDetector는 PermitAllPaths 기준으로 UNAUTHORIZED를 자동 탐지한다.
        // 따라서 인증 필요 엔드포인트는 SecurityRuleDetector 결과에 UNAUTHORIZED가 있어야 한다.
        // @ApiErrorCodes가 UNAUTHORIZED를 선언하면 중복 → noStaleApiErrorCodes_buildGate가 잡는다.
        List<String> uncovered = endpoints.stream()
            .filter(endpoint -> {
                Set<String> security = securityDetector.detect(endpoint);
                Set<String> annotation = annotationResults.getOrDefault(endpoint, Set.of());
                // 인증 필요(UNAUTHORIZED 자동 탐지)인데 ASM/어노테이션에 아무것도 없는 엔드포인트
                // 정상: SecurityRuleDetector가 UNAUTHORIZED를 자동 처리하므로 이 게이트는 통과
                return security.contains("UNAUTHORIZED")
                    && asmResults.getOrDefault(endpoint, Set.of()).isEmpty()
                    && annotation.isEmpty();
            })
            .sorted()
            .toList();

        assertThat(uncovered)
            .as("완전 탐지 조건 미충족 엔드포인트 (ASM 탐지 없음 + @ApiErrorCodes 없음):\n%s\n"
                + "→ 도메인 예외 throw 추가 또는 @ApiErrorCodes 선언 필요",
                String.join("\n", uncovered))
            .isEmpty();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 빌드 게이트: 스테일 @ApiErrorCodes 검사
    //
    // @ApiErrorCodes에 선언된 코드가 ASM 자동 탐지와 중복이면 빌드 실패.
    // 어노테이션을 최소화하여 수동 선언 부담을 줄인다.
    // ──────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("스테일 @ApiErrorCodes 빌드 게이트 — ASM 자동 탐지 코드와 중복된 @ApiErrorCodes 선언이 있으면 빌드 실패")
    void noStaleApiErrorCodes_buildGate() throws IOException {
        Path classesRoot = Paths.get("build/classes/java/main");
        ErrorCodeScanner scanner = new ErrorCodeScanner(classesRoot);

        Map<String, Set<String>> asmResults = scanner.scanAsmOnly("com.jangingmall.backend");
        Map<String, Set<String>> annotationResults = scanner.scanAnnotationsOnly("com.jangingmall.backend");

        List<String> staleEntries = annotationResults.entrySet().stream()
            .filter(e -> {
                Set<String> asmCodes = asmResults.getOrDefault(e.getKey(), Set.of());
                Set<String> stale = new LinkedHashSet<>(e.getValue());
                stale.retainAll(asmCodes);
                return !stale.isEmpty();
            })
            .map(e -> {
                Set<String> stale = new LinkedHashSet<>(e.getValue());
                stale.retainAll(asmResults.getOrDefault(e.getKey(), Set.of()));
                return e.getKey() + " → 중복 코드: " + stale;
            })
            .sorted()
            .toList();

        assertThat(staleEntries)
            .as("@ApiErrorCodes에 ASM 자동 탐지 코드와 중복 선언된 항목:\n%s\n"
                + "→ 해당 코드를 @ApiErrorCodes에서 제거하세요",
                String.join("\n", staleEntries))
            .isEmpty();
    }

    private Set<String> findEndpoint(String endpointKey) {
        return scanResult.entrySet().stream()
            .filter(e -> endpointKey.equals(e.getKey()) || endpointMatches(e.getKey(), endpointKey))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "엔드포인트 '" + endpointKey + "'가 스캔 결과에 없습니다.\n실제 결과: " + scanResult.keySet()
            ));
    }

    private boolean endpointMatches(String actual, String expected) {
        String pattern = expected.replaceAll("\\{[^}]+}", "[^/]+");
        return actual.matches(pattern.replace("/", "\\/"));
    }
}
