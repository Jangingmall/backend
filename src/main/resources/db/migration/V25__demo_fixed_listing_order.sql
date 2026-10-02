-- 시연용 기본 목록(최신순) 맨 앞 8개를 고정한다. 정렬은 created_at DESC, id DESC 이므로
-- created_at 을 서로 다른 값으로 마이그레이션 시각 이후로 지정해 순서가 항상 같게 한다.
UPDATE product p
   SET created_at = NOW() + ((9 - v.pos) * INTERVAL '1 minute')
  FROM (VALUES
    (1, '전주 합죽선 · 매화선'),
    (2, '청자 운학문 찻잔'),
    (3, '분청 귀얄 찻잔'),
    (4, '홍매 삼작 노리개'),
    (5, '수자수 모란도 액자'),
    (6, '한지 무드 조명'),
    (7, '하늘빛 한산모시 스카프'),
    (8, '나전 명함집')
  ) AS v(pos, title)
 WHERE p.title = v.title;
