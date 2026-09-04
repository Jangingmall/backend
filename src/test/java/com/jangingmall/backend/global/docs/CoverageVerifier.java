package com.jangingmall.backend.global.docs;

import java.util.*;

/**
 * 엔드포인트별 완전 탐지 조건(A-D) 충족 여부를 판정한다.
 *
 * <p>완전 탐지 조건 — 4가지 모두 충족 시 @ApiErrorCodes 생략 가능:
 * <ul>
 *   A. BusinessException 서브클래스 경로 — ASM depth-5 탐지 (ErrorCodeScanner)
 *   B. UNAUTHORIZED — SecurityRuleDetector (PermitAllPaths 기준)
 *   C. CONFLICT — WriteTxDetector (@Transactional write-tx 탐지)
 *   D. CONCURRENT_UPDATE — OptimisticLockEntityRegistry (@Version entity) + write-tx
 * </ul>
 *
 * <p>C, D는 과-태깅 방향 허용: write-tx가 있으면 항상 추가하므로 false-positive 가능.
 * false-negative(누락)는 없다. noStaleApiErrorCodes_buildGate가 불필요한 @ApiErrorCodes를 제거한다.
 */
class CoverageVerifier {

    record CoverageResult(
        String endpoint,
        Set<String> asmCodes,
        Set<String> securityCodes,
        Set<String> writeTxCodes,
        Set<String> optimisticLockCodes,
        Set<String> annotationCodes
    ) {
        Set<String> allAutoDetected() {
            Set<String> all = new LinkedHashSet<>();
            all.addAll(asmCodes);
            all.addAll(securityCodes);
            all.addAll(writeTxCodes);
            all.addAll(optimisticLockCodes);
            return all;
        }

        Set<String> totalCodes() {
            Set<String> all = new LinkedHashSet<>(allAutoDetected());
            all.addAll(annotationCodes);
            return all;
        }

        /**
         * @ApiErrorCodes에 선언된 코드 중 자동 탐지 결과와 중복된 코드.
         * 이 코드들은 @ApiErrorCodes에서 제거해야 한다.
         */
        Set<String> staleAnnotationCodes() {
            Set<String> stale = new LinkedHashSet<>(annotationCodes);
            stale.retainAll(allAutoDetected());
            return stale;
        }

        /**
         * @ApiErrorCodes 생략 가능 여부 판정.
         * annotationCodes가 allAutoDetected의 부분집합이면 생략 가능.
         */
        boolean isAnnotationOmittable() {
            return allAutoDetected().containsAll(annotationCodes);
        }
    }

    /**
     * 엔드포인트 키 → CoverageResult 맵을 구성한다.
     *
     * @param asmResults           ErrorCodeScanner.scan() 결과 (ASM 탐지 + @ApiErrorCodes 병합 전)
     * @param annotationResults    resolveApiErrorCodes()로 읽은 @ApiErrorCodes 결과
     * @param securityDetector     SecurityRuleDetector 인스턴스
     * @param isWriteTxEndpoint    엔드포인트 → write-tx 여부 (WriteTxDetector 결과)
     * @param versionedEntities    @Version 엔티티 내부명 집합 (OptimisticLockEntityRegistry 결과)
     */
    Map<String, CoverageResult> verify(
        Map<String, Set<String>> asmResults,
        Map<String, Set<String>> annotationResults,
        SecurityRuleDetector securityDetector,
        Map<String, Boolean> isWriteTxEndpoint,
        Set<String> versionedEntities
    ) {
        Map<String, CoverageResult> results = new LinkedHashMap<>();

        Set<String> endpoints = new LinkedHashSet<>(asmResults.keySet());
        endpoints.addAll(annotationResults.keySet());

        for (String endpoint : endpoints) {
            Set<String> asmCodes = asmResults.getOrDefault(endpoint, Set.of());
            Set<String> annotationCodes = annotationResults.getOrDefault(endpoint, Set.of());

            // B: Security 탐지
            Set<String> securityCodes = securityDetector.detect(endpoint);

            // C: write-tx → CONFLICT 과-태깅 (false-positive 허용)
            boolean isWrite = Boolean.TRUE.equals(isWriteTxEndpoint.get(endpoint));
            Set<String> writeTxCodes = isWrite ? Set.of("CONFLICT") : Set.of();

            // D: write-tx + @Version 엔티티 → CONCURRENT_UPDATE 과-태깅
            // 현재 구현: 엔티티가 하나라도 있고 write-tx면 추가 (도메인별 세분화는 @ApiErrorCodes로)
            Set<String> optimisticLockCodes = (isWrite && !versionedEntities.isEmpty())
                ? Set.of("CONCURRENT_UPDATE") : Set.of();

            results.put(endpoint, new CoverageResult(
                endpoint, asmCodes, securityCodes, writeTxCodes, optimisticLockCodes, annotationCodes
            ));
        }
        return results;
    }
}
