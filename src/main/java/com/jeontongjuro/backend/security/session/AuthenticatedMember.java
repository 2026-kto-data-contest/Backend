package com.jeontongjuro.backend.security.session;

import com.jeontongjuro.backend.member.MemberRole;

public record AuthenticatedMember(Long id, String email, MemberRole role) {
}
