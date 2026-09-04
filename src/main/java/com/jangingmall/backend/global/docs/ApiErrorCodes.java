package com.jangingmall.backend.global.docs;

import com.jangingmall.backend.global.exception.ErrorCode;

import java.lang.annotation.*;

/**
 * ASM 스캐너가 탐지하지 못하는 에러코드를 Controller 메서드에 명시적으로 선언한다.
 *
 * <p>탐지 불가 대상:
 * <ul>
 *   <li>Security 필터 예외 (UNAUTHORIZED, FORBIDDEN via Spring Security)
 *   <li>DataIntegrityViolationException → CONFLICT
 *   <li>ObjectOptimisticLockingFailureException → CONCURRENT_UPDATE
 *   <li>CannotAcquireLockException → CONCURRENT_UPDATE
 * </ul>
 *
 * <p>사용 예:
 * <pre>
 * {@literal @}PostMapping("/products")
 * {@literal @}ApiErrorCodes({ErrorCode.CONFLICT, ErrorCode.UNAUTHORIZED})
 * public ResponseEntity<ProductResponse> create(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiErrorCodes {
    ErrorCode[] value();
}
