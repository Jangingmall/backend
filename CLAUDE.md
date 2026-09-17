## REST Docs 

- 컨트롤러 엔드포인트 = REST Docs 테스트 필수
- 새 컨트롤러 엔드포인트를 추가하거나 기존 엔드포인트를 수정할 때, 반드시 대응하는 REST Docs 테스트가 있어야 한다.
- 등 모든 HTTP 메서드 매핑은 성공 경로 REST Docs 문서화 필수
- 테스트 없이 컨트롤러 엔드포인트를 커밋하는 것을 금지한다
- 문서화된 테스트는 `MockMvcRestDocumentationWrapper.document()` + `resource(ResourceSnippetParameters.builder()...)` 형태로 작성한다
