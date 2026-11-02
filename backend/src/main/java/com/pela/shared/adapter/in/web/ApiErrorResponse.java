package com.pela.shared.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Khuôn lỗi chung của API: {@code {"error":{"code","message","details"}}} (OpenAPI components.schemas.Error). */
public record ApiErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, Object details) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return of(code, message, null);
    }

    public static ApiErrorResponse of(String code, String message, Object details) {
        return new ApiErrorResponse(new Body(code, message, details));
    }
}
