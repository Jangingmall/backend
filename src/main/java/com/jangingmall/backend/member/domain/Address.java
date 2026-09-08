package com.jangingmall.backend.member.domain;

import jakarta.persistence.*;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "address_id") private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "recipient_name", nullable = false, length = 50) private String recipientName;
    @Column(nullable = false, length = 20) private String phone;
    @Column(name = "zip_code", nullable = false, length = 10) private String zipCode;
    @Column(nullable = false, length = 255) private String address1;
    @Column(length = 255) private String address2;
    @Column(name = "is_default", nullable = false) private boolean defaultAddress;

    public Address(Long memberId, String recipientName, String phone, String zipCode, String address1, String address2) {
        this.memberId = memberId;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address1 = address1;
        this.address2 = address2;
    }

    public void update(Optional<String> recipientName, Optional<String> phone, Optional<String> zipCode,
                       Optional<String> address1, Optional<String> address2) {
        recipientName.ifPresent(value -> this.recipientName = value);
        phone.ifPresent(value -> this.phone = value);
        zipCode.ifPresent(value -> this.zipCode = value);
        address1.ifPresent(value -> this.address1 = value);
        address2.ifPresent(value -> this.address2 = value);
    }

    public void chooseDefault(boolean selected) {
        defaultAddress = selected;
    }
}
