package com.pela.identity.adapter.in.web;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.adapter.in.web.CurrentUserId;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final GetCurrentUserUseCase currentUser;

    public MeController(GetCurrentUserUseCase currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping("/v1/me")
    public MeResponse me(@CurrentUserId UUID userId) {
        return MeResponse.of(currentUser.currentUser(userId));
    }

    /** Admin web gọi để xác nhận quyền Admin (đi qua AdminAccessInterceptor như mọi /v1/admin/**). */
    @GetMapping("/v1/admin/me")
    public MeResponse adminMe(@CurrentUserId UUID userId) {
        return MeResponse.of(currentUser.currentUser(userId));
    }

    public record MeResponse(UUID userId, String email, String fullName, String username, String avatarUrl,
            String role, String status, String cefrLevel, String timezone) {

        static MeResponse of(UserAccount account) {
            return new MeResponse(account.userId(), account.email(), account.fullName(), account.username(),
                    account.avatarUrl(), account.role().dbValue(), account.status().dbValue(), account.cefrLevel(),
                    account.timezone());
        }
    }
}
