package com.pela.shared.adapter.in.web;

import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.shared.domain.exception.LockedException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.PelaException;
import com.pela.shared.domain.exception.ServiceUnavailableException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.UnauthorizedException;
import com.pela.shared.domain.exception.ValidationFailedException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Chuyển mọi exception thành khuôn lỗi {@code {"error":{"code","message","details"}}}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PelaException.class)
    ResponseEntity<ApiErrorResponse> handleBusiness(PelaException ex) {
        HttpStatus status = switch (ex) {
            case NotFoundException e -> HttpStatus.NOT_FOUND;
            case ConflictException e -> HttpStatus.CONFLICT;
            case ForbiddenException e -> HttpStatus.FORBIDDEN;
            case LockedException e -> HttpStatus.LOCKED;
            case TooManyRequestsException e -> HttpStatus.TOO_MANY_REQUESTS;
            case ValidationFailedException e -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UnauthorizedException e -> HttpStatus.UNAUTHORIZED;
            case ServiceUnavailableException e -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return respond(status, ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details()));
    }

    /** Bean Validation trên body (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::fieldError)
                .toList();
        return validationFailed(fields);
    }

    /** Bean Validation trên tham số method (@PathVariable, @RequestParam…). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<Map<String, String>> params = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream().map(e -> Map.of(
                        "field", String.valueOf(r.getMethodParameter().getParameterName()),
                        "message", String.valueOf(e.getDefaultMessage()))))
                .toList();
        return validationFailed(params);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        return validationFailed(violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("MALFORMED_REQUEST", "Request body is missing or malformed"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, ApiErrorResponse.of("BAD_REQUEST", "Invalid value for parameter",
                Map.of("field", ex.getName())));
    }

    /** Lỗi 401 phát sinh trong controller, ví dụ khi resolve {@code @CurrentUserId}. */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(SecurityErrors.unauthenticated(ex));
    }

    /** Lỗi 403 từ method security (@PreAuthorize…) trong controller. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, SecurityErrors.forbidden());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        // Lỗi chuẩn của Spring MVC (404 không có route, 405, 415, thiếu tham số…) tự mang mã trạng thái
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode statusCode = errorResponse.getStatusCode();
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            if (status != null && status.is4xxClientError()) {
                return respond(status, ApiErrorResponse.of(status.name(), status.getReasonPhrase()));
            }
        }
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorResponse.of("INTERNAL_ERROR", "Unexpected server error"));
    }

    private static ResponseEntity<ApiErrorResponse> validationFailed(Object details) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorResponse.of("VALIDATION_FAILED", "Request validation failed", details));
    }

    private static Map<String, String> fieldError(FieldError e) {
        return Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage()));
    }

    private static ResponseEntity<ApiErrorResponse> respond(HttpStatus status, ApiErrorResponse body) {
        return ResponseEntity.status(status).body(body);
    }
}
