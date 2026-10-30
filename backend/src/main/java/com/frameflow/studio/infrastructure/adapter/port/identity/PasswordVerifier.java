package com.frameflow.studio.infrastructure.adapter.port.identity;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import com.frameflow.studio.domain.identity.adapter.port.IPasswordVerifier;

@Component
public class PasswordVerifier implements IPasswordVerifier {
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public PasswordVerifier(PasswordEncoder encoder) {
        this.encoder = encoder;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public boolean matches(String password, String encoding) {
        boolean matches = encoder.matches(password, encoding == null ? dummyHash : encoding);
        return encoding != null && matches;
    }

    @Override
    public String encode(String password) {
        return encoder.encode(password);
    }
}
