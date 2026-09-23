# H. External Dependency / Mock Requirements

## 식별된 외부 의존성

| 시스템 | 연관 API | Mock 필요 | Sandbox | Failure 시나리오 |
|--------|---------|-----------|---------|-----------------|
| Toss Payments | POST /api/payments/confirm, POST /api/payments/webhooks/toss, POST /api/payments/{paymentId}/cancel | YES | YES (Toss 개발자 센터) | 결제 실패(카드 한도 초과), webhook 미수신, timeout |
| 카카오 OAuth | GET /api/member/oauth2/kakao, POST /api/member/oauth2/exchange | YES | YES (카카오 개발자 센터) | code 만료, state 불일치, 계정 없음 |
| AI 서비스 | POST /api/content/{productId}/generations, POST /internal/generations/{id}/completion | YES | BACKEND_INFO_REQUIRED | 생성 실패, timeout, callback 미수신 |
| S3 / 이미지 스토리지 | POST /api/images/presigned-url, DELETE /api/images/{imageId} | YES | 로컬 MinIO 가능 | presigned URL 만료, 업로드 실패 |

## Mock 전략 권고

```
Toss: Toss 공식 sandbox 환경 사용
      실패 케이스: 카드번호 0000000000000001 (한도초과) 등 Toss sandbox 오류코드 활용

카카오 OAuth: 실제 sandbox 필요 (자동화 어려움)
              BACKEND_INFO_REQUIRED: 테스트용 OAuth code 발급 방법

AI 서비스: /internal/generations/{id}/completion mock 엔드포인트 필요
           BACKEND_INFO_REQUIRED: AI 서비스 mock 방법

이미지: presigned URL → 실제 S3 업로드 or 로컬 mock
```

## Failure / Timeout 시나리오

| 시나리오 | 검증 방법 | 자동화 |
|---------|---------|--------|
| Toss 결제 실패 응답 | POST /api/payments/fail | AUTO (sandbox) |
| Toss webhook 지연 | 수동 or timer mock | CONFIG_REQUIRED |
| AI 생성 timeout | generation-poll- 반복 조회 | AUTO |
| S3 presigned URL 만료 | 만료된 URL로 PUT | AUTO |
