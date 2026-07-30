package com.jeontongjuro.backend.testsupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 로컬 PostgreSQL(docker compose up -d) 기동 여부 확인.
 * DB가 없으면 Spring 컨텍스트가 뜨기 전에 해당 테스트를 스킵시키기 위한 @EnabledIf 조건이다.
 * (DB는 PostgreSQL 단일 방침 — H2 대체 금지이므로, 미기동 시 실패 대신 명시적 스킵을 택한다.)
 */
public final class LocalPostgres {

    private LocalPostgres() {
    }

    public static boolean isUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
