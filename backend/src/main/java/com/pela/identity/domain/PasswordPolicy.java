package com.pela.identity.domain;

import java.util.ArrayList;
import java.util.List;

/** Auth.Register.Validate: tối thiểu 8 ký tự, có ít nhất 1 chữ hoa, 1 chữ thường và 1 chữ số. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    /** Danh sách luật bị vi phạm (rỗng = hợp lệ). Không bao giờ trả lại chính mật khẩu. */
    public static List<String> violations(String password) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            violations.add("must be at least " + MIN_LENGTH + " characters");
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("must contain an uppercase letter");
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("must contain a lowercase letter");
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            violations.add("must contain a digit");
        }
        return violations;
    }
}
