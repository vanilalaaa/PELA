package com.pela.platform.adapter.out.gemini;

import com.pela.shared.application.port.out.AiCallException;
import com.pela.shared.application.port.out.AiCallException.Kind;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Chạy một lời gọi Gemini với timeout và đổi mọi lỗi sang {@link AiCallException}. Chỉ log thao tác,
 * mã HTTP và loại lỗi — không bao giờ log prompt, output hay API key.
 */
final class GeminiHttp {

    private static final Logger log = LoggerFactory.getLogger(GeminiHttp.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private GeminiHttp() {
    }

    static <T> T call(String operation, Duration timeout, Supplier<T> request) {
        Future<T> future = EXECUTOR.submit(request::get);
        try {
            return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw failure(operation, Kind.TIMEOUT, "timed out after " + timeout, null);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(operation, Kind.UPSTREAM_ERROR, "interrupted", e);
        } catch (ExecutionException e) {
            throw translate(operation, e.getCause());
        }
    }

    static AiCallException translate(String operation, Throwable error) {
        if (error instanceof AiCallException ai) {
            return ai;
        }
        if (error instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            Kind kind = status == 429 ? Kind.RATE_LIMITED
                    : status == 408 || status == 504 ? Kind.TIMEOUT
                    : Kind.UPSTREAM_ERROR;
            return failure(operation, kind, "HTTP " + status, http);
        }
        if (error instanceof ResourceAccessException io && isTimeout(io)) {
            return failure(operation, Kind.TIMEOUT, "I/O timeout", io);
        }
        return failure(operation, Kind.UPSTREAM_ERROR, error.getClass().getSimpleName(), error);
    }

    static AiCallException failure(String operation, Kind kind, String detail, Throwable cause) {
        log.warn("Gemini {} failed: kind={}, {}", operation, kind, detail);
        return new AiCallException(kind, "Gemini " + operation + " failed: " + kind, cause);
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
