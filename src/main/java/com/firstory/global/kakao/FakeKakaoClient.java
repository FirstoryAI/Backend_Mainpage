package com.firstory.global.kakao;

import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카카오 없이 돌리는 가짜 (firstory.kakao.mode=fake, local·test).
 * 테스트는 register로 코드마다 회원을 정한다. 등록하지 않은 코드는 코드로 회원번호(fake-해시)를 만들고
 * 이메일 kakao-{코드}@example.com(카카오 인증함)을 준다. 코드 "fail"은 KAKAO_LOGIN_FAILED.
 */
public class FakeKakaoClient implements KakaoClient {

    public static final String FAILING_CODE = "fail";

    private final Map<String, KakaoUser> users = new ConcurrentHashMap<>();

    public void register(String code, KakaoUser user) {
        users.put(code, user);
    }

    @Override
    public KakaoUser fetchUser(String authorizationCode, String redirectUri) {
        if (FAILING_CODE.equals(authorizationCode)) {
            throw new BusinessException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
        KakaoUser registered = users.get(authorizationCode);
        if (registered != null) {
            return registered;
        }
        return new KakaoUser("fake-" + Integer.toHexString(authorizationCode.hashCode()),
                "kakao-" + authorizationCode + "@example.com", true);
    }
}
