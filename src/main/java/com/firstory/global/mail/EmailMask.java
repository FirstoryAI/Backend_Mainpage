package com.firstory.global.mail;

/**
 * 로그에 남길 때 이메일을 가린다. parent@example.com → p*****@example.com
 */
public final class EmailMask {

    private EmailMask() {
    }

    public static String mask(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "*".repeat(Math.max(1, at - 1)) + email.substring(at);
    }
}
