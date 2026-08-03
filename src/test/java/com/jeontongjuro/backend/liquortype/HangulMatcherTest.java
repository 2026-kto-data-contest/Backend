package com.jeontongjuro.backend.liquortype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 한글 단어경계 매칭 단위 테스트(DB 불필요). ★분포를 단정하지 않는다 — 규칙 결정론만 검증한다.
 */
class HangulMatcherTest {

    @Test
    @DisplayName("단어경계 '진'(gin): 앞·뒤 한글 접촉 출현은 비매칭(진맥소주·진도홍주 오탐 방지)")
    void wordBoundaryRejectsHangulAdjacent() {
        assertThat(HangulMatcher.contains("진맥소주", "진", true)).isFalse();
        assertThat(HangulMatcher.contains("진도홍주 아라리35%", "진", true)).isFalse();
        assertThat(HangulMatcher.contains("안동 진맥 소주", "진", true)).isFalse();
        assertThat(HangulMatcher.contains("오곡 진상주", "진", true)).isFalse();
    }

    @Test
    @DisplayName("단어경계 '진'(gin): 앞이 공백/문자열끝 등 비한글이면 매칭('퍼플 진')")
    void wordBoundaryAcceptsNonHangulAdjacent() {
        assertThat(HangulMatcher.contains("퍼플 진", "진", true)).isTrue();
        assertThat(HangulMatcher.contains("진 토닉", "진", true)).isTrue();   // 앞=문자열시작, 뒤=공백
        assertThat(HangulMatcher.contains("GIN 진(gin)", "진", true)).isTrue(); // 앞=공백, 뒤='('
    }

    @Test
    @DisplayName("wordBoundary=false는 단순 부분문자열('막걸리' ⊂ '유자생막걸리')")
    void plainContainsIsSubstring() {
        assertThat(HangulMatcher.contains("유자생막걸리", "막걸리", false)).isTrue();
        assertThat(HangulMatcher.contains("세종청주생막걸리", "청주", false)).isTrue();
    }

    @Test
    @DisplayName("null·빈 입력 안전: false 반환")
    void nullAndEmptyAreSafe() {
        assertThat(HangulMatcher.contains(null, "진", true)).isFalse();
        assertThat(HangulMatcher.contains("퍼플 진", null, true)).isFalse();
        assertThat(HangulMatcher.contains("퍼플 진", "", true)).isFalse();
    }

    @Test
    @DisplayName("isHangul: 완성형 음절만 한글로 본다(영문·숫자·기호는 경계)")
    void isHangulBasics() {
        assertThat(HangulMatcher.isHangul('진')).isTrue();
        assertThat(HangulMatcher.isHangul('가')).isTrue();
        assertThat(HangulMatcher.isHangul(' ')).isFalse();
        assertThat(HangulMatcher.isHangul('A')).isFalse();
        assertThat(HangulMatcher.isHangul('4')).isFalse();
        assertThat(HangulMatcher.isHangul('(')).isFalse();
    }
}
