# API 요구사항: AI 생성 초안 조회 (`DRAFT_READY`)

## 1. 배경 / 문제
- 판매자 AI 제작 화면은 생성 상태가 `DRAFT_READY` 가 되면 "AI 초안 확인이 필요합니다 … 초안 조회·승인 연결이 없어 편집 화면을 열 수 없습니다"에서 멈춘다.
- 현재 서버는 `DRAFT_READY` 에서 초안 **내용**을 저장·제공하지 않는다(`GenerationDeadlineScheduler.applyDraftReady` 는 상태만 바꾼다). `GET .../contents` 는 이전 생성의 문서일 수 있어 초안으로 쓸 수 없다.
- 이미 있는 것: 상태 조회 `GET .../generations/{id}`, 렌더 수동 요청 `POST .../generations/{id}/render`, 콘텐츠 승인 `POST .../contents/{contentId}/approve`.

## 2. 요구사항 (이 API 한 개)
`GET /api/content/products/{productId}/generations/{generationId}/draft`

| 항목 | 내용 |
|---|---|
| 권한 | `ARTISAN`, 해당 상품 소유자만 (아니면 403) |
| 성공 | `200` — 상태가 `DRAFT_READY` 인 생성 건의 초안 |
| 응답 `data` | `generationId`, `productId`, `status`(`DRAFT_READY`), `draftDocument`(react_document 2.0 JSON 객체, 이미지 노드에 `props.src` 포함), `detailFeatures[]`(`title`,`body`), `updatedAt` |
| 실패 | `404` 생성 건 없음 · `422` 상태가 `DRAFT_READY` 아님(`QUEUED/PROCESSING/ANALYZING/COMPLETED/FAILED`) · `403` 소유자 아님 |
| 저장 | 초안이 만들어지는 시점(`DRAFT_READY` 전이)에 `content_generation` 에 초안 JSON 을 저장한다. 렌더 완료(`COMPLETED`) 후에는 기존대로 `content.react_document` 가 최종본이다 |

## 3. 화면 흐름 (프론트)
1. 생성 요청 → 2초 폴링 → `DRAFT_READY`
2. `GET .../draft` 로 초안을 받아 **읽기 전용 미리보기**를 보여 준다(편집은 `COMPLETED` 이후).
3. 판매자가 "이대로 렌더링" → `POST .../render`(202) → 계속 폴링 → `COMPLETED` → `GET .../contents` 로 편집 화면.

## 4. 인수 조건
- `DRAFT_READY` 가 아닌 상태에서 호출하면 422, 존재하지 않으면 404, 다른 장인이면 403.
- 응답의 `draftDocument` 이미지는 `props.src` 만으로 표시된다.
- **REST Docs 필수**: 성공(200)과 422·403·404 케이스를 `MockMvcRestDocumentationWrapper.document()` + `resource(ResourceSnippetParameters.builder()...)` 로 작성하고, `GET .../generations/{id}` 문서에 "`DRAFT_READY` 이면 이 API 로 초안 조회" 안내를 넣는다.
- 단위 테스트: 상태별 분기, 소유자 검증, 초안이 없을 때(저장 전) 처리.

## 5. 확인이 필요한 점 (백엔드 ↔ AI)
- GenAI 의 `DRAFT_READY` 상태 응답에 초안 JSON 이 들어오는지. 안 오면 GenAI 쪽에 초안 반환 필드를 요청해야 한다.
- 초안 보존 기간(재생성 시 덮어쓰기 여부).

## 6. 임시 대응 (구현 전)
- 프론트는 `DRAFT_READY` 를 진행 중으로 보고 폴링을 이어 가며, "지금 렌더링 요청" 버튼에서 `POST .../render` 를 호출한다(`docs/seller-ai-draft-ready-flow.md`).
