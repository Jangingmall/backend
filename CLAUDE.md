## 멀티 에이전트 작업 충돌 방지

새 작업 시작 전 반드시 `.claude/agent/agent_live.md`를 읽는다.
- 브랜치가 겹치면 작업 전에 사용자에게 알린다.
- 접근 파일이 겹치면 해당 항목을 사용자에게 보고하고 지시를 기다린다.
- 작업 시작 시 자신의 항목을 `agent_live.md`에 등록한다.
- 작업 완료 시 상태를 `완료`로 업데이트한다.

## REST Docs 

- 컨트롤러 엔드포인트 = REST Docs 테스트 필수
- 새 컨트롤러 엔드포인트를 추가하거나 기존 엔드포인트를 수정할 때, 반드시 대응하는 REST Docs 테스트가 있어야 한다.
- 등 모든 HTTP 메서드 매핑은 성공 경로 REST Docs 문서화 필수
- 테스트 없이 컨트롤러 엔드포인트를 커밋하는 것을 금지한다
- 문서화된 테스트는 `MockMvcRestDocumentationWrapper.document()` + `resource(ResourceSnippetParameters.builder()...)` 형태로 작성한다
