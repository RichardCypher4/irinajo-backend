package com.irinajo.common;

/** Keeps phone numbers out of logs in plain text. */
public final class Mask {
    private Mask() {}

    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 4) + "*****" + phone.substring(phone.length() - 3);
    }
}
