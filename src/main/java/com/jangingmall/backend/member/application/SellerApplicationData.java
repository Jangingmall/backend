package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.SellerApplication;
import java.time.LocalDateTime;

public record SellerApplicationData(Long applicationId, Long memberId, String businessName,
                                    SellerApplication.Status status, LocalDateTime submittedAt,
                                    String introduction, String businessLicenseImageUrl, String rejectReason,
                                    Pipeline pipeline, SellerApplication.Qualification qualificationTier) {
    public static SellerApplicationData from(SellerApplication application) {
        return new SellerApplicationData(application.getId(), application.getMemberId(), application.getBusinessName(),
            application.getStatus(), application.getSubmittedAt(), application.getIntroduction(),
            application.getBusinessLicenseImageUrl(), application.getRejectionReason(),
            new Pipeline(application.getDocumentReview(), application.getCraftsmanshipReview(),
                application.getDigitalConversion(), application.getOrderSystemIntegration()), application.getQualificationTier());
    }

    public record Pipeline(SellerApplication.Stage documentReview, SellerApplication.Stage craftsmanshipReview,
                           SellerApplication.Stage digitalConversion, SellerApplication.Stage orderSystemIntegration) {}
}
