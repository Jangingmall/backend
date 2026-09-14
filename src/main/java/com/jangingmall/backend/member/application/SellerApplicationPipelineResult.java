package com.jangingmall.backend.member.application;

import com.jangingmall.backend.member.domain.SellerApplication;

public record SellerApplicationPipelineResult(
    Long applicationId,
    SellerApplicationData.Pipeline pipeline,
    SellerApplication.Status overallStatus,
    SellerApplication.Qualification qualificationTier
) {
    public static SellerApplicationPipelineResult from(SellerApplication application) {
        SellerApplicationData data = SellerApplicationData.from(application);
        return new SellerApplicationPipelineResult(data.applicationId(), data.pipeline(), data.status(), data.qualificationTier());
    }
}
