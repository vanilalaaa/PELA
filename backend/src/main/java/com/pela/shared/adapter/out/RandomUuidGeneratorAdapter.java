package com.pela.shared.adapter.out;

import com.pela.shared.application.port.out.IdGeneratorPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RandomUuidGeneratorAdapter implements IdGeneratorPort {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
