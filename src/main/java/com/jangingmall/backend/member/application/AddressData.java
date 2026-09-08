package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.Address;

public record AddressData(Long addressId, String recipientName, String phone, String zipCode,
                          String address1, String address2, boolean isDefault) {
    public static AddressData from(Address address) {
        return new AddressData(address.getId(), address.getRecipientName(), address.getPhone(),
            address.getZipCode(), address.getAddress1(), address.getAddress2(), address.isDefaultAddress());
    }
}
