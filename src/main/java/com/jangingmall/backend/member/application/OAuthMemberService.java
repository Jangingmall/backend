package com.jangingmall.backend.member.application;

import com.jangingmall.backend.global.exception.DomainException;
import com.jangingmall.backend.global.exception.ErrorCode;
import com.jangingmall.backend.member.domain.*;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service @RequiredArgsConstructor
public class OAuthMemberService {
    private static final String TICKET="oauth-ticket";
    private static final String ONBOARDING="oauth-onboarding";
    private final MemberRepository members;
    private final MemberSocialAccountRepository accounts;
    private final OneTimeTokenStore tokens;
    private final ObjectMapper json;

    public String createTicket(OAuthIdentity identity) {
        var account=accounts.findByRegistrationIdAndProviderUserId(identity.provider(),identity.subject());
        if (account.isPresent()) {
            Long memberId=account.get().getMemberId();
            members.findById(memberId).filter(Member::canLogIn).orElseThrow(()->new DomainException(ErrorCode.UNAUTHORIZED));
            return tokens.issue(TICKET,json.writeValueAsString(new Grant(memberId,identity)),Duration.ofMinutes(1));
        }
        requireNewEmail(identity.email());
        return tokens.issue(TICKET,json.writeValueAsString(new Grant(null,identity)),Duration.ofMinutes(1));
    }

    public Grant exchange(String ticket) {
        return json.readValue(tokens.consume(TICKET,ticket).orElseThrow(()->new DomainException(ErrorCode.UNAUTHORIZED)),Grant.class);
    }

    public String onboarding(OAuthIdentity identity) {
        return tokens.issue(ONBOARDING,json.writeValueAsString(identity),Duration.ofMinutes(10));
    }

    @Transactional
    public MemberSignupResult complete(String token,Completion command) {
        requireAgreements(command);
        OAuthIdentity identity=json.readValue(tokens.consume(ONBOARDING,token)
            .orElseThrow(()->new DomainException(ErrorCode.UNAUTHORIZED)),OAuthIdentity.class);
        requireNewEmail(identity.email());
        var member=Member.register(identity.email(),null,command.name(),command.phone(),MemberRole.USER,
            command.age14OrOlder(),command.termsOfService(),command.privacyCollection(),command.marketing());
        member.activate();
        member=members.save(member);
        accounts.save(new MemberSocialAccount(member.getId(),identity.provider(),identity.subject(),identity.email()));
        return new MemberSignupResult(member.getId(),member.getEmail(),member.getStatus());
    }

    private void requireNewEmail(String email) {
        if (members.existsByEmail(email)) {
            throw new DomainException(ErrorCode.CONFLICT);
        }
    }

    private void requireAgreements(Completion command) {
        if (!command.age14OrOlder() || !command.termsOfService() || !command.privacyCollection()) {
            throw new DomainException(ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    public record Grant(Long memberId,OAuthIdentity identity) {}
    public record Completion(String name,String phone,boolean age14OrOlder,boolean termsOfService,
                             boolean privacyCollection,boolean marketing) {}
}
