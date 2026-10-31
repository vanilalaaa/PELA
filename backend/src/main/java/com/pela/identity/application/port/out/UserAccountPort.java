package com.pela.identity.application.port.out;

import com.pela.identity.domain.LoginState;
import com.pela.identity.domain.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Bảng users (hồ sơ PELA). */
public interface UserAccountPort {

    /** Email đã chuẩn hoá chữ thường. */
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(UUID userId);

    /**
     * Đọc–sửa–ghi bộ đếm đăng nhập một cách nguyên tử (khoá dòng trong lúc đổi), để các lần đăng nhập
     * sai đồng thời không làm mất lượt đếm. Trả trạng thái sau khi đổi.
     */
    LoginState updateLoginState(UUID userId, UnaryOperator<LoginState> change);

    /** unverified → active sau khi Supabase xác nhận email. */
    void markEmailVerified(UUID userId);

    /**
     * suspended → active nếu suspended_until đã qua. Idempotent: gọi lại hay gọi cho tài khoản không
     * thoả điều kiện thì không đổi gì. Trả true nếu đã mở khoá.
     */
    boolean releaseExpiredSuspension(UUID userId, Instant now);

    /** Như {@link #releaseExpiredSuspension} cho mọi tài khoản; trả số tài khoản được mở khoá. */
    int releaseAllExpiredSuspensions(Instant now);
}
