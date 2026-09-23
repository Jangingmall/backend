# F. Test Data Setup / Cleanup Strategy

## 환경 변수 (Postman / Newman)

| Variable | 설명 | 생성 방법 |
|----------|------|-----------|
| `{{baseUrl}}` | API 서버 기준 URL | CONFIG_REQUIRED |
| `{{accessToken}}` | USER 역할 JWT | POST /api/member/login |
| `{{artisanToken}}` | ARTISAN 역할 JWT | POST /api/member/login (장인 계정) |
| `{{adminToken}}` | ADMIN 역할 JWT | CONFIG_REQUIRED |
| `{{memberId}}` | 테스트 회원 ID | POST /api/member/signup |
| `{{productId}}` | 테스트 상품 ID | POST /api/products |
| `{{cartItemId}}` | 장바구니 항목 ID | POST /api/payments/cart/items |
| `{{orderId}}` | 주문 ID | POST /api/payments/orders |
| `{{paymentId}}` | 결제 ID | POST /api/payments/confirm |
| `{{addressId}}` | 배송지 ID | POST /api/member/me/addresses |
| `{{notificationId}}` | 알림 ID | POST /api/notifications |
| `{{timestamp}}` | 현재 시각 ISO8601 | Runtime: new Date().toISOString() |
| `{{randomEmail}}` | 고유 이메일 | Runtime: `test+{random}@example.com` |
| `{{randomString}}` | 랜덤 문자열 | Runtime: Math.random().toString(36) |
| `{{artisanId}}` | 장인 ID | GET /api/member/artisans |
| `{{sessionId}}` | 챗봇 세션 ID | POST /api/chatbot/sessions |
| `{{generationId}}` | AI 생성 ID | POST /api/content/{productId}/generations |
| `{{contentId}}` | 콘텐츠 ID | GET /api/content/{productId}/contents |

## Setup 순서 (테스트 전 실행)

```
1. POST /api/member/signup          → {{userId}} (USER)
2. POST /api/member/login           → {{accessToken}}
3. POST /api/member/me/addresses    → {{addressId}}
4. [ARTISAN 계정] POST /api/member/login → {{artisanToken}}
5. POST /api/products               → {{productId}}
6. POST /api/payments/cart/items    → {{cartItemId}}
7. POST /api/payments/orders        → {{orderId}}
```

## Cleanup 전략

| 리소스 | Cleanup API | 가능 여부 |
|--------|-------------|-----------|
| 회원 | DELETE /api/member/me | 가능 |
| 상품 | DELETE /api/products/{productId} | 가능 |
| 장바구니 항목 | DELETE /api/payments/cart/items/{cartItemId} | 가능 |
| 배송지 | DELETE /api/member/me/addresses/{addressId} | 가능 |
| 알림 | DELETE /api/notifications/{notificationId} | 가능 |
| 이미지 | DELETE /api/images/{imageId} | 가능 |
| 주문 | CLEANUP_GAP — 삭제 API 없음 | DB 직접 또는 BACKEND_INFO_REQUIRED |
| 결제 | CLEANUP_GAP — 삭제 API 없음 | DB 직접 또는 BACKEND_INFO_REQUIRED |
| 반품 | CLEANUP_GAP — 삭제 API 없음 | DB 직접 또는 BACKEND_INFO_REQUIRED |
| 찜 | DELETE /api/products/{productId}/wish | 가능 |
| 장인 구독 | DELETE /api/member/artisans/{artisanId}/subscribe | 가능 |
| 챗봇 세션 | DELETE /api/chatbot/sessions/{sessionId} | 가능 |

## 자동 생성 값 예시 (Postman Pre-request Script)

```javascript
pm.environment.set('randomEmail', `test+${Date.now()}@example.com`);
pm.environment.set('randomString', Math.random().toString(36).substring(2, 10));
pm.environment.set('timestamp', new Date().toISOString());
```
