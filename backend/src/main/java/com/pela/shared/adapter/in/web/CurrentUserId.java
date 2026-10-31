package com.pela.shared.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tiêm userId của người đang gọi API (claim {@code sub} trong JWT Supabase) vào tham số {@code UUID}
 * của controller. Đây là nguồn userId duy nhất: không bao giờ đọc userId từ body/query (SEC-4).
 *
 * <pre>{@code
 * @GetMapping("/v1/me")
 * MeResponse me(@CurrentUserId UUID userId) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
