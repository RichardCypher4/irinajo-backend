package com.irinajo.common;

import java.util.regex.Pattern;

/**
 * Nigerian mobile numbers only, in E.164: +234 then 7/8/9, then 0/1, then 8 digits
 * (e.g. +2348031234567). This blocks non-Nigerian and premium/short-code ranges by default.
 */
public final class Phones {
    private static final Pattern NG_MOBILE = Pattern.compile("^\\+234[789][01]\\d{8}$");

    private Phones() {}

    public static String normalise(String raw) {
        if (raw == null) throw ApiException.badRequest("PHONE_INVALID", "Phone number is required");
        String p = raw.replaceAll("[\\s()-]", "");
        if (p.startsWith("0") && p.length() == 11) p = "+234" + p.substring(1);
        else if (p.startsWith("234")) p = "+" + p;
        if (!NG_MOBILE.matcher(p).matches()) {
            throw ApiException.badRequest("PHONE_NOT_SUPPORTED", "Use a Nigerian mobile number in the format +234...");
        }
        return p;
    }
}
