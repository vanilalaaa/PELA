package com.pela.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1", "Str0ngPassword", "aB3aB3aB3"})
    void acceptsPasswordsMeetingAuthRegisterValidate(String password) {
        assertThat(PasswordPolicy.violations(password)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "Abc1, at least 8",
            "abcdefg1, uppercase",
            "ABCDEFG1, lowercase",
            "Abcdefgh, digit",
    })
    void rejectsWeakPasswords(String password, String expected) {
        assertThat(PasswordPolicy.violations(password)).anyMatch(v -> v.contains(expected));
    }
}
