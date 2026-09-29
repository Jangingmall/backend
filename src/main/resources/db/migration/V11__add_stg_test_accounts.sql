-- 스테이징 테스트 계정 3개 (stgUser / stgArtisan / stgAdmin)
INSERT INTO member (email, password_hash, name, nickname, phone, role, status,
                    age14_or_older, terms_agreed, privacy_agreed, marketing_agreed,
                    created_at, updated_at)
VALUES
    ('stgUser@midam.store',    '$2a$10$E1szMzvew6N/Shu.91XKRuOolyLt.oxPevoxnPV3a0it6AOqwjkHa', '테스트유저',   '테스트유저',    '010-0000-9001', 'USER',    'ACTIVE', TRUE, TRUE, TRUE, FALSE, NOW(), NOW()),
    ('stgArtisan@midam.store', '$2a$10$g5cpfGbK9g1KUDV6eMvhxeagW13HOUvfmjreuW8Ccd7Wr9LqmWfQK', '테스트장인',   '테스트장인공방', '010-0000-9002', 'ARTISAN', 'ACTIVE', TRUE, TRUE, TRUE, FALSE, NOW(), NOW()),
    ('stgAdmin@midam.store',   '$2a$10$NvdRI29kCysaU6OLcd9B8eCbp5eL7rMlj/sXCVHo20hoki3QZMqFq', '테스트어드민', '테스트어드민',   '010-0000-9003', 'ADMIN',   'ACTIVE', TRUE, TRUE, TRUE, FALSE, NOW(), NOW())
ON CONFLICT (email) DO UPDATE
    SET password_hash  = EXCLUDED.password_hash,
        status         = 'ACTIVE',
        updated_at     = NOW();
