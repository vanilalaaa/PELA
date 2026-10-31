package com.pela.identity.application.usecase;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.identity.application.port.out.UserAccountPort;
import com.pela.identity.domain.UserAccount;
import com.pela.shared.domain.exception.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService implements GetCurrentUserUseCase {

    private final UserAccountPort accounts;

    public CurrentUserService(UserAccountPort accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserAccount currentUser(UUID userId) {
        return accounts.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User profile not found", null));
    }
}
