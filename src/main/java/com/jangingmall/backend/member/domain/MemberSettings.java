package com.jangingmall.backend.member.domain;

import jakarta.persistence.*;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "member_settings") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSettings {
    @Id @Column(name = "member_id") private Long memberId;
    @Column(name = "dark_mode", nullable = false) private boolean darkMode;
    @Column(nullable = false) private boolean marketing;

    public MemberSettings(Long memberId, boolean marketing) {
        this.memberId = memberId;
        this.marketing = marketing;
    }

    public void update(Optional<Boolean> darkMode, Optional<Boolean> marketing) {
        darkMode.ifPresent(value -> this.darkMode = value);
        marketing.ifPresent(value -> this.marketing = value);
    }
}
