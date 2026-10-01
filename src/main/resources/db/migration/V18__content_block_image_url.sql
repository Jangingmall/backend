-- AI 가 만든 상세페이지 이미지는 업로드 이미지(image_upload)로 등록돼 있지 않아 image_id 로 가리킬 수 없다.
-- 공개 주소(https://img…/ai-generated/…)를 그대로 보관해 소비자 상품 상세에 보여 준다.
ALTER TABLE content_block ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
