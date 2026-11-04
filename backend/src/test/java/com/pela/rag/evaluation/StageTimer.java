package com.pela.rag.evaluation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Đo thời gian từng bước của một lần chạy UC-24 bằng cách gói các port (interface) bằng dynamic proxy. Cộng dồn nano giây theo
 * tên bước (db, embed, llm, filter, cache_write); gọi {@link #reset()} trước mỗi lần chạy. Không thêm gì vào code chạy thật.
 */
final class StageTimer {

    static final String DB = "db";
    static final String EMBED = "embed";
    static final String LLM = "llm";
    static final String FILTER = "filter";
    static final String CACHE_WRITE = "cache_write";

    private final Map<String, Long> nanos = new LinkedHashMap<>();

    void reset() {
        nanos.clear();
    }

    /** Mili giây đã cộng dồn của một bước (0 nếu bước đó không chạy). */
    double millis(String stage) {
        return nanos.getOrDefault(stage, 0L) / 1_000_000.0;
    }

    double totalMillis() {
        return nanos.values().stream().mapToLong(Long::longValue).sum() / 1_000_000.0;
    }

    /** Gói {@code target} để mọi lời gọi qua interface {@code port} được cộng vào bước {@code stageOf(method)}. */
    @SuppressWarnings("unchecked")
    <T> T wrap(Class<T> port, T target, Function<Method, String> stageOf) {
        return (T) Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (proxy, method, args) -> {
            long start = System.nanoTime();
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                nanos.merge(stageOf.apply(method), System.nanoTime() - start, Long::sum);
            }
        });
    }

    <T> T wrap(Class<T> port, T target, String stage) {
        return wrap(port, target, method -> stage);
    }
}
