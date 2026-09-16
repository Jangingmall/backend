package com.jangingmall.backend.image.application;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class UlidGenerator {

    private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String next() {
        long timestamp = System.currentTimeMillis();
        char[] value = new char[26];
        for (int index = 9; index >= 0; index--) {
            value[index] = BASE32[(int) (timestamp & 31)];
            timestamp >>>= 5;
        }

        BigInteger entropy = new BigInteger(1, random.generateSeed(10));
        for (int index = 25; index >= 10; index--) {
            value[index] = BASE32[entropy.and(BigInteger.valueOf(31)).intValue()];
            entropy = entropy.shiftRight(5);
        }
        return new String(value);
    }
}
