package com.jangingmall.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.Address;
import com.jangingmall.backend.member.domain.AddressRepository;
import com.jangingmall.backend.member.domain.MemberRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private MemberAccess access;
    @Mock private AddressRepository addresses;
    private AddressService service;

    @BeforeEach
    void setUp() {
        service = new AddressService(access, addresses);
    }

    @Test
    @DisplayName("첫 배송지는 요청값과 무관하게 기본 배송지로 저장한다")
    void makesFirstAddressDefault() {
        when(addresses.findByMemberIdOrderByIdAsc(1L)).thenReturn(List.of());
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            ReflectionTestUtils.setField(address, "id", 10L);
            return address;
        });

        AddressData result = service.create(1L, data(false));

        assertThat(result.addressId()).isEqualTo(10L);
        assertThat(result.isDefault()).isTrue();
        verify(access).lockRole(1L, MemberRole.USER);
    }

    @Test
    @DisplayName("새 기본 배송지를 지정하면 기존 기본 배송지를 해제한다")
    void replacesExistingDefault() {
        Address current = address(10L, 1L, true);
        when(addresses.findByMemberIdOrderByIdAsc(1L)).thenReturn(List.of(current));
        when(addresses.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            ReflectionTestUtils.setField(address, "id", 11L);
            return address;
        });

        AddressData result = service.create(1L, data(true));

        assertThat(current.isDefaultAddress()).isFalse();
        assertThat(result.isDefault()).isTrue();
        verify(addresses).flush();
    }

    @Test
    @DisplayName("다른 회원의 배송지는 수정할 수 없다")
    void rejectsAnotherMembersAddress() {
        when(addresses.findById(10L)).thenReturn(Optional.of(address(10L, 2L, false)));

        assertThatThrownBy(() -> service.update(1L, 10L, noChanges()))
            .isInstanceOfSatisfying(DomainException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("기본 배송지를 삭제하면 남은 배송지 하나가 기본 배송지가 된다")
    void choosesReplacementAfterDeletingDefault() {
        Address deleted = address(10L, 1L, true);
        Address replacement = address(11L, 1L, false);
        when(addresses.findById(10L)).thenReturn(Optional.of(deleted));
        when(addresses.findByMemberIdOrderByIdAsc(1L)).thenReturn(List.of(replacement));

        service.delete(1L, 10L);

        assertThat(replacement.isDefaultAddress()).isTrue();
        verify(addresses).delete(deleted);
        verify(addresses).flush();
    }

    private AddressData data(boolean isDefault) {
        return new AddressData(null, "김도공", "01012345678", "12345", "서울시 종로구", "101호", isDefault);
    }

    private Address address(Long id, Long memberId, boolean isDefault) {
        Address address = new Address(memberId, "김도공", "01012345678", "12345", "서울시 종로구", "101호");
        address.chooseDefault(isDefault);
        ReflectionTestUtils.setField(address, "id", id);
        return address;
    }

    private AddressService.Changes noChanges() {
        return new AddressService.Changes(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
