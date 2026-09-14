package com.jangingmall.backend.member.presentation;

import com.jangingmall.backend.global.exception.*;
import com.jangingmall.backend.global.security.JwtProperties;
import com.jangingmall.backend.member.application.*;
import com.jangingmall.backend.member.domain.MemberRole;
import com.jangingmall.backend.member.presentation.dto.MemberSignupRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/member/oauth2") @RequiredArgsConstructor
public class OAuthController {
    private final OAuthMemberService oauth;
    private final MemberAuthenticationService authentication;
    private final JwtProperties jwt;

    @GetMapping("/kakao")
    public ResponseEntity<Void> kakao() {
        return ResponseEntity.status(302).location(URI.create("/oauth2/authorization/kakao")).build();
    }

    @GetMapping("/google")
    public ResponseEntity<Void> google() {
        return ResponseEntity.status(302).location(URI.create("/oauth2/authorization/google")).build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ExchangeResponse> exchange(@CookieValue(value="oauthTicket",required=false) String ticket) {
        var grant=oauth.exchange(Optional.ofNullable(ticket).filter(value->!value.isBlank())
            .orElseThrow(()->new DomainException(ErrorCode.UNAUTHORIZED)));
        var builder=ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,MemberCookies.ticket("",Duration.ZERO,jwt).toString());
        if (grant.memberId()==null) {
            return builder.header(HttpHeaders.SET_COOKIE,MemberCookies.onboarding(oauth.onboarding(grant.identity()),Duration.ofMinutes(10),jwt).toString())
                .body(new ExchangeResponse(true,null));
        }
        var session=authentication.socialSession(grant.memberId());
        return builder.header(HttpHeaders.SET_COOKIE,MemberCookies.refresh(session.refreshToken(),jwt).toString())
            .body(new ExchangeResponse(false,session.accessToken()));
    }

    @PostMapping("/complete-profile")
    public ResponseEntity<CompletionResponse> complete(@CookieValue(value="oauthOnboarding",required=false) String token,
                                                       @Valid @RequestBody CompleteProfile request) {
        token=Optional.ofNullable(token).filter(value->!value.isBlank()).orElseThrow(()->new DomainException(ErrorCode.UNAUTHORIZED));
        var result=oauth.complete(token,request.toCommand());
        var session=authentication.socialSession(result.memberId());
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE,MemberCookies.onboarding("",Duration.ZERO,jwt).toString())
            .header(HttpHeaders.SET_COOKIE,MemberCookies.refresh(session.refreshToken(),jwt).toString())
            .body(new CompletionResponse(result.memberId(),result.email(),MemberRole.USER,session.accessToken()));
    }

    public record ExchangeResponse(boolean onboardingRequired,String accessToken) {}
    public record CompletionResponse(Long memberId,String email,MemberRole role,String accessToken) {}
    public record CompleteProfile(@NotBlank @Size(max=50) String name,@NotBlank @Pattern(regexp="\\d{9,20}") String phone,
                                  @NotNull @Valid MemberSignupRequest.Agreements agreements) {
        public OAuthMemberService.Completion toCommand() {
            return new OAuthMemberService.Completion(name,phone,agreements.age14OrOlder(),agreements.termsOfService(),
                agreements.privacyCollection(),Boolean.TRUE.equals(agreements.marketing()));
        }
    }
}
