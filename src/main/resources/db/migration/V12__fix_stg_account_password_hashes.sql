-- V11 bcrypt 해시 오류 수정 ($2y$ → $2a$: Spring BCryptPasswordEncoder는 $2a$/$2b$만 인식)
UPDATE member SET password_hash = '$2a$10$HbwngblpckqcBFlxi6CUb.fzKdGEyp76rrNoMoMIjYumULH/yz7LG' WHERE email = 'stgUser@midam.store';
UPDATE member SET password_hash = '$2a$10$uBxyvJZf118CAg.Dbf5qwuvzegACAIlcIRdK3SAjsFsvKmBOiYGNm' WHERE email = 'stgArtisan@midam.store';
UPDATE member SET password_hash = '$2a$10$a.OjlGLVth1j203K9JViBe18UpG/lChiWOYR6HhbilIiwKCy/Zpqy' WHERE email = 'stgAdmin@midam.store';
