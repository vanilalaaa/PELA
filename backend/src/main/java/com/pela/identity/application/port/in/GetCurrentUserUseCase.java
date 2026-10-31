package com.pela.identity.application.port.in;

import com.pela.identity.domain.UserAccount;
import java.util.UUID;

/** GET /v1/me: hồ sơ của người đang đăng nhập (SEC-4). */
public interface GetCurrentUserUseCase {

    UserAccount currentUser(UUID userId);
}
