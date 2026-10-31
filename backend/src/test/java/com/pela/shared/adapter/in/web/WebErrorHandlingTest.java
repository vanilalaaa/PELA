package com.pela.shared.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pela.platform.config.SecurityConfig;
import com.pela.shared.domain.exception.ConflictException;
import com.pela.shared.domain.exception.ForbiddenException;
import com.pela.shared.domain.exception.LockedException;
import com.pela.shared.domain.exception.NotFoundException;
import com.pela.shared.domain.exception.TooManyRequestsException;
import com.pela.shared.domain.exception.ValidationFailedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = WebErrorHandlingTest.TestController.class)
@Import({SecurityConfig.class, WebErrorHandlingTest.TestController.class})
class WebErrorHandlingTest {

    private static final String USER_ID = "7d3c5a2e-1b4f-4c8a-9e6d-2f1a0b3c4d5e";

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {

        record Body(@NotBlank String name) {
        }

        @GetMapping("/test/throw/{kind}")
        void throwBusiness(@PathVariable String kind) {
            switch (kind) {
                case "not-found" -> throw new NotFoundException("Lesson not found");
                case "conflict" -> throw new ConflictException("Already exists");
                case "forbidden" -> throw new ForbiddenException("Not your resource");
                case "locked" -> throw new LockedException("FINAL_TEST_LOCKED", "Final test is locked",
                        Map.of("starsToUnlock", 30));
                case "too-many" -> throw new TooManyRequestsException("Daily limit reached");
                case "invalid" -> throw new ValidationFailedException("Rule violated");
                case "access-denied" -> throw new AccessDeniedException("denied");
                default -> throw new IllegalStateException("secret internal detail");
            }
        }

        @PostMapping("/test/body")
        void body(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/me")
        Map<String, Object> me(@CurrentUserId UUID userId, @RequestParam(required = false) String userIdParam) {
            return Map.of("userId", userId);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "not-found, 404, NOT_FOUND",
            "conflict, 409, CONFLICT",
            "forbidden, 403, FORBIDDEN",
            "locked, 423, FINAL_TEST_LOCKED",
            "too-many, 429, TOO_MANY_REQUESTS",
            "invalid, 422, VALIDATION_FAILED",
            "access-denied, 403, FORBIDDEN",
            "unexpected, 500, INTERNAL_ERROR",
    })
    void businessExceptionsUseErrorEnvelope(String kind, int status, String code) throws Exception {
        mvc.perform(get("/test/throw/" + kind).with(jwt()))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value(code))
                .andExpect(jsonPath("$.error.message").isString());
    }

    @Test
    void detailsAreReturnedWhenPresent() throws Exception {
        mvc.perform(get("/test/throw/locked").with(jwt()))
                .andExpect(jsonPath("$.error.details.starsToUnlock").value(30));
    }

    @Test
    void unexpectedErrorDoesNotLeakInternalMessage() throws Exception {
        mvc.perform(get("/test/throw/unexpected").with(jwt()))
                .andExpect(content().json(
                        "{\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\"Unexpected server error\"}}",
                        JsonCompareMode.STRICT));
    }

    @Test
    void beanValidationReturns422WithFieldDetails() throws Exception {
        mvc.perform(post("/test/body").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mvc.perform(post("/test/body").with(jwt()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownRouteReturns404Envelope() throws Exception {
        mvc.perform(get("/test/does-not-exist").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void missingTokenReturns401Envelope() throws Exception {
        mvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void malformedTokenReturns401InvalidToken() throws Exception {
        mvc.perform(get("/test/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void currentUserIdComesFromJwtSubjectOnly() throws Exception {
        mvc.perform(get("/test/me")
                        .param("userId", UUID.randomUUID().toString())
                        .param("userIdParam", UUID.randomUUID().toString())
                        .with(jwt().jwt(j -> j.subject(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID));
    }

    @Test
    void nonUuidSubjectReturns401InvalidToken() throws Exception {
        mvc.perform(get("/test/me").with(jwt().jwt(j -> j.subject("not-a-uuid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_TOKEN"));
    }
}
