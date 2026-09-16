package com.jangingmall.backend.member.infrastructure;

import com.jangingmall.backend.member.domain.MemberSettings;
import org.springframework.data.jpa.repository.JpaRepository;

interface MemberSettingsJpaRepository extends JpaRepository<MemberSettings, Long> {}
