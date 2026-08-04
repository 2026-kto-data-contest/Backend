package com.jeontongjuro.backend.auth.dto.response;

public record LoginResult(String sessionToken, String nextPath) {
}
