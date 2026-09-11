package com.jangingmall.backend.payment.infrastructure;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Address;
import com.jangingmall.backend.member.domain.AddressRepository;
import com.jangingmall.backend.payment.application.ShippingAddressReader;
import com.jangingmall.backend.payment.domain.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberShippingAddressReader implements ShippingAddressReader {
    private final AddressRepository addresses;

    @Override
    public PurchaseOrder.ShippingAddress findOwned(Long memberId, Long addressId) {
        Address address = addresses.findById(addressId)
            .filter(candidate -> candidate.getMemberId().equals(memberId))
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        return new PurchaseOrder.ShippingAddress(address.getId(), address.getRecipientName(), address.getPhone(),
            address.getZipCode(), address.getAddress1(), address.getAddress2());
    }
}
