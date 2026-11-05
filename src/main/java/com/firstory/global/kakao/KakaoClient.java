package com.firstory.global.kakao;

/**
 * 카카오 로그인. 프론트가 카카오에서 받은 인가 코드로 카카오 회원을 알아낸다.
 * 카카오 서버를 부르므로 DB 트랜잭션 밖에서 부른다.
 * 코드가 틀렸거나 만료됐으면 KAKAO_LOGIN_FAILED, 카카오에 닿지 못하면 KAKAO_UNAVAILABLE.
 */
public interface KakaoClient {

    KakaoUser fetchUser(String authorizationCode, String redirectUri);
}
