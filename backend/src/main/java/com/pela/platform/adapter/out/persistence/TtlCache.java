package com.pela.platform.adapter.out.persistence;

import com.pela.shared.application.port.out.ClockPort;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Cache theo key có hạn sống, cache cả kết quả rỗng (để key chưa cấu hình không truy vấn DB liên tục). */
final class TtlCache<V> {

    private final Duration ttl;
    private final ClockPort clock;
    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();

    TtlCache(Duration ttl, ClockPort clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    Optional<V> get(String key, Function<String, Optional<V>> loader) {
        Instant now = clock.now();
        Entry<V> entry = entries.get(key);
        if (entry == null || !now.isBefore(entry.expiresAt())) {
            entry = new Entry<>(loader.apply(key), now.plus(ttl));
            entries.put(key, entry);
        }
        return entry.value();
    }

    private record Entry<V>(Optional<V> value, Instant expiresAt) {
    }
}
