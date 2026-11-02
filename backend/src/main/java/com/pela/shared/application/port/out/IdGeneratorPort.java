package com.pela.shared.application.port.out;

import java.util.UUID;

/**
 * Sinh id (UUID) cho bản ghi do server tạo. Không gọi {@code UUID.randomUUID()} trực tiếp trong
 * domain/use case (ArchitectureTest kiểm tra). Id do client sinh (answer_log…) không đi qua port này.
 */
public interface IdGeneratorPort {

    UUID newId();
}
