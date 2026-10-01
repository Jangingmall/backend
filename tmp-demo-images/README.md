# 임시 시연 사진 업로드 (merge 전 삭제)

| 위치 | 내용 |
|---|---|
| `tmp-demo-images/hapjukseon-maehwa/source/` | Flow 1 전주 합죽선 · 매화선 사진 9장 (WebP, 최대 1600px) |
| `scripts/demo-images/upload_demo_images.py` | 정식 이미지 API로 올리고 공개 링크를 확인하는 도구 |
| `.github/workflows/tmp-upload-demo-images.yml` | 이 브랜치에 push 하면 실행되는 임시 워크플로 |

## 실행에 필요한 저장소 설정
- Secret `STG_API_BASE_URL`: STG 백엔드 주소 (예: `https://<STG API 도메인>`)
- Secret `STG_AGENT_TOKEN`: AGENT 권한 토큰 (`/api/images/presigned-url`, `/internal/images/verify` 호출용)
- Variable `DEMO_OWNER_MEMBER_ID`: 사진을 소유할 회원 ID (시연 상품의 장인 ID)

## 시연 상품 문구 (Flow 1)
- 상품명(15자 이내): 전주 합죽선 · 매화선
- 제작 과정·상품 설명: 담양 왕대를 3년 건조해 손으로 겉대·속대를 깎고, 한지를 겹겹이 붙여 선면을 만들었습니다. 매화를 한 획씩 직접 그려 같은 부채가 하나도 없습니다.
- 사용·보관 관리 방법: 펼칠 때는 아래에서 위로 천천히 펴 주세요. 사용 후에는 접어 직사광선과 습기를 피해 보관하면 오래 쓸 수 있습니다. 물티슈·물세척은 한지가 상하니 피해 주세요.

## merge 전 원복
`git rm -r tmp-demo-images scripts/demo-images .github/workflows/tmp-upload-demo-images.yml` 후 커밋.
