package com.jangingmall.backend.member.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter
@Table(name="member_social_account",uniqueConstraints=@UniqueConstraint(columnNames={"registration_id","provider_user_id"}))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class MemberSocialAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="member_id",nullable=false) private Long memberId;
    @Column(name="registration_id",nullable=false,length=20) private String registrationId;
    @Column(name="provider_user_id",nullable=false,length=255) private String providerUserId;
    @Column(name="provider_email",length=255) private String providerEmail;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt=LocalDateTime.now();

    public MemberSocialAccount(Long memberId,String registrationId,String providerUserId,String email) {
        this.memberId=memberId;
        this.registrationId=registrationId;
        this.providerUserId=providerUserId;
        providerEmail=email;
    }
}
