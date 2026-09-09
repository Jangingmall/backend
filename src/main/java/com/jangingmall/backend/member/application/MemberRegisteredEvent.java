package com.jangingmall.backend.member.application;

public record MemberRegisteredEvent(Long memberId, String email) {
}
