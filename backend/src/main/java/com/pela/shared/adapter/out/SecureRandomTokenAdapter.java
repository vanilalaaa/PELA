package com.pela.shared.adapter.out;

import com.pela.shared.application.port.out.SecureTokenPort;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomTokenAdapter implements SecureTokenPort {

    private static final int BYTES = 32;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String newToken() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
