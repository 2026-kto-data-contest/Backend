package com.jeontongjuro.backend.search.recent;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RecentSearchException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RecentSearchException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
