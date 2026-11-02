package com.pela.shared.application.port.out;

/** Sinh chuỗi ngẫu nhiên an toàn (URL-safe, >= 256 bit) cho token dùng một lần. */
public interface SecureTokenPort {

    String newToken();
}
