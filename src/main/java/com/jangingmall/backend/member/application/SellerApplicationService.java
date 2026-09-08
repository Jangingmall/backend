package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerApplicationService {
    private final MemberAccess access;
    private final SellerApplicationRepository applications;
    private final ArtisanProfileRepository artisans;
    private final MemberReadRepository reads;

    @Transactional
    public SellerApplicationData apply(Long memberId, String businessName, String introduction, String licenseUrl) {
        access.lockExactRole(memberId, MemberRole.USER);
        if (applications.existsByMemberIdAndStatus(memberId, SellerApplication.Status.PENDING)) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
        return SellerApplicationData.from(applications.save(new SellerApplication(memberId, businessName, introduction, licenseUrl)));
    }

    @Transactional(readOnly = true)
    public SellerApplicationData mine(Long memberId) {
        access.active(memberId);
        return applications.findFirstByMemberIdOrderByIdDesc(memberId).map(SellerApplicationData::from)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CursorPage<SellerApplicationData> list(Long adminId, PageRequest page, String status) {
        access.requireRole(adminId, MemberRole.ADMIN);
        return reads.applications(page, status);
    }

    @Transactional(readOnly = true)
    public SellerApplicationData detail(Long adminId, Long applicationId) {
        access.requireRole(adminId, MemberRole.ADMIN);
        return SellerApplicationData.from(find(applicationId));
    }

    @Transactional
    public SellerApplicationData approve(Long adminId, Long applicationId) {
        access.requireRole(adminId, MemberRole.ADMIN);
        SellerApplication application = find(applicationId);
        var member = access.lock(application.getMemberId());
        application.approve(adminId);
        member.approveArtisan();
        artisans.save(new ArtisanProfile(application));
        applications.flush();
        return SellerApplicationData.from(application);
    }

    @Transactional
    public SellerApplicationData reject(Long adminId, Long applicationId, String reason) {
        access.requireRole(adminId, MemberRole.ADMIN);
        SellerApplication application = find(applicationId);
        application.reject(adminId, reason);
        applications.flush();
        return SellerApplicationData.from(application);
    }

    @Transactional
    public SellerApplicationPipelineResult updatePipeline(Long adminId, Long applicationId, SellerApplication.Step step,
                                                          SellerApplication.Stage stage, SellerApplication.Qualification qualification) {
        access.requireRole(adminId, MemberRole.ADMIN);
        SellerApplication application = find(applicationId);
        boolean approved = application.updatePipeline(step, stage, qualification);
        application.assignReviewer(adminId);
        if (approved) {
            var member = access.lock(application.getMemberId());
            member.approveArtisan();
            artisans.save(new ArtisanProfile(application));
        }
        applications.flush();
        return SellerApplicationPipelineResult.from(application);
    }

    private SellerApplication find(Long id) {
        return applications.findById(id).orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
    }
}
