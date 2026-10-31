package com.pela.identity.domain;

/** Trạng thái tài khoản (cột users.status). */
public enum AccountStatus {
    UNVERIFIED("unverified"),
    ACTIVE("active"),
    SUSPENDED("suspended"),
    PENDING_DELETION("pending_deletion");

    private final String dbValue;

    AccountStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static AccountStatus fromDb(String value) {
        for (AccountStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown account status: " + value);
    }
}
