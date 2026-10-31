package com.pela.identity.domain;

/** Vai trò tài khoản (cột users.role). Release 1.0 chỉ có một vai trò Admin phẳng (UC-17). */
public enum Role {
    LEARNER("learner"),
    ADMIN("admin");

    private final String dbValue;

    Role(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static Role fromDb(String value) {
        for (Role role : values()) {
            if (role.dbValue.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
