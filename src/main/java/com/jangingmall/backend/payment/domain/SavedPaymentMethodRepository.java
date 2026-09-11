package com.jangingmall.backend.payment.domain;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodRepository {
    SavedPaymentMethod save(SavedPaymentMethod paymentMethod);
    List<SavedPaymentMethod> findByMemberIdOrderByIdAsc(Long memberId);
    Optional<SavedPaymentMethod> findByMemberIdAndCardFingerprint(Long memberId, String fingerprint);
    Optional<SavedPaymentMethod> findById(Long paymentMethodId);
    long countByMemberId(Long memberId);
    void delete(SavedPaymentMethod paymentMethod);
}
