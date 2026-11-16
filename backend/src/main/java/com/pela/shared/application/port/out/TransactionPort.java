package com.pela.shared.application.port.out;

import java.util.function.Supplier;

/**
 * Chạy nhiều thao tác ghi (có thể của nhiều module, mỗi module qua port riêng) trong một transaction DB. Ném exception
 * thì rollback toàn bộ.
 */
public interface TransactionPort {

    <T> T inTransaction(Supplier<T> work);
}
