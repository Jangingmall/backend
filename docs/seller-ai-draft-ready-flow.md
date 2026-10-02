# AI 생성 `DRAFT_READY` 처리 흐름 (프론트 전달용)

"AI 초안 확인이 필요합니다 — 초안 조회·승인 연결이 없다"는 안내는 백엔드 API 부재가 아니라 프론트가 `/render`·`/approve` 를 연결하지 않아서 나온다.
두 API 모두 REST Docs 에 문서화되어 있다.

| 목적 | API | REST Docs 식별자 | 비고 |
|---|---|---|---|
| 상태 조회(2초 폴링) | `GET /api/content/products/{productId}/generations/{generationId}` | `generation-poll-processing` · `-draft-ready` · `-completed` · `-failed` · `-not-found` | `QUEUED → PROCESSING → ANALYZING → DRAFT_READY → COMPLETED \| FAILED` |
| 렌더링 수동 요청 | `POST /api/content/products/{productId}/generations/{generationId}/render` | `generation-render`, `generation-render-not-allowed`(422), 403 | `DRAFT_READY` 일 때만 202. 같은 요청 반복해도 중복 생성 없음 |
| 콘텐츠 승인 | `POST /api/content/products/{productId}/contents/{contentId}/approve` | `content-approve` | 승인 시 `DRAFT_READY` 건이 있으면 렌더도 함께 요청 |

## 서버 동작
1. `DRAFT_READY` 가 되면 서버가 **자동으로** GenAI 에 최종 렌더링을 요청하고, 마감(3시간)까지 주기적으로 같은 키로 재요청한다.
2. 렌더가 끝나 콜백이 오면 `react_document` 를 저장하고 상태가 `COMPLETED` 가 된다. 이때부터 `GET /api/content/products/{productId}/contents` 로 편집 문서를 읽는다.
3. 마감까지 끝나지 않으면 `FAILED`(재생성 필요).

## 프론트 권장 처리
- `DRAFT_READY` 는 오류가 아니라 **진행 중**이다. 계속 폴링하고 "상세페이지를 완성하는 중입니다"로 보여 준다.
- 오래 걸릴 때를 위해 "지금 렌더링 요청" 버튼에서 `POST .../render` 를 호출한다(응답 202 후 계속 폴링).
- `DRAFT_READY` 동안 `/contents` 는 이전 생성의 문서일 수 있으므로 편집 화면에 쓰지 않는다(`COMPLETED` 이후에만).

## 아직 없는 것
`DRAFT_READY` 단계의 초안 **내용** 조회 API. 지금은 초안을 저장하지 않으므로 사용자가 렌더 전에 초안을 검수할 수 없다.
검수 단계가 필요하면 백엔드에서 초안을 저장하고 조회 API 를 추가해야 한다(별도 작업).
