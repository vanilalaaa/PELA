package com.pela.identity.application.port.in;

import java.util.UUID;

/** UC-01 1.0: đăng ký bằng email (Auth.Register). */
public interface RegisterUseCase {

    RegisteredAccount register(RegisterCommand command);

    record RegisterCommand(String fullName, String email, String password, String passwordConfirmation) {

        @Override
        public String toString() {
            return "RegisterCommand[***]";
        }
    }

    record RegisteredAccount(UUID userId, String email, String status) {
    }
}
