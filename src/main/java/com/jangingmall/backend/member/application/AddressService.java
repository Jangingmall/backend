package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.*;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressService {
    private final MemberAccess access;
    private final AddressRepository addresses;

    @Transactional(readOnly = true)
    public List<AddressData> list(Long memberId) {
        access.requireRole(memberId, MemberRole.USER);
        return addresses.findByMemberIdOrderByIdAsc(memberId).stream().map(AddressData::from).toList();
    }

    @Transactional
    public AddressData create(Long memberId, AddressData data) {
        access.lockRole(memberId, MemberRole.USER);
        List<Address> existing = addresses.findByMemberIdOrderByIdAsc(memberId);
        Address address = new Address(memberId, data.recipientName(), data.phone(), data.zipCode(), data.address1(), data.address2());
        if (data.isDefault() || existing.isEmpty()) {
            replaceDefault(existing, address);
        }
        return AddressData.from(addresses.save(address));
    }

    @Transactional
    public AddressData update(Long memberId, Long addressId, Changes changes) {
        access.lockRole(memberId, MemberRole.USER);
        Address address = owned(addressId, memberId);
        address.update(changes.recipientName(), changes.phone(), changes.zipCode(), changes.address1(), changes.address2());
        changes.isDefault().ifPresent(selected -> changeDefault(memberId, address, selected));
        return AddressData.from(address);
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        access.lockRole(memberId, MemberRole.USER);
        Address address = owned(addressId, memberId);
        addresses.delete(address);
        addresses.flush();
        if (address.isDefaultAddress()) {
            addresses.findByMemberIdOrderByIdAsc(memberId).stream().findFirst()
                .ifPresent(next -> next.chooseDefault(true));
        }
    }

    private Address owned(Long addressId, Long memberId) {
        Address address = addresses.findById(addressId).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
        if (!address.getMemberId().equals(memberId)) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        return address;
    }

    private void changeDefault(Long memberId, Address address, boolean selected) {
        if (selected) {
            replaceDefault(addresses.findByMemberIdOrderByIdAsc(memberId), address);
            return;
        }
        if (address.isDefaultAddress()) {
            var replacement = addresses.findByMemberIdOrderByIdAsc(memberId).stream()
                .filter(candidate -> !candidate.getId().equals(address.getId())).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.BUSINESS_RULE_VIOLATION));
            replaceDefault(List.of(address), replacement);
        }
    }

    private void replaceDefault(List<Address> existing, Address selected) {
        existing.forEach(address -> address.chooseDefault(false));
        addresses.flush();
        selected.chooseDefault(true);
    }

    public record Changes(Optional<String> recipientName, Optional<String> phone, Optional<String> zipCode,
                          Optional<String> address1, Optional<String> address2, Optional<Boolean> isDefault) {}
}
