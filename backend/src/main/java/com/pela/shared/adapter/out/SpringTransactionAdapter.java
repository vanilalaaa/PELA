package com.pela.shared.adapter.out;

import com.pela.shared.application.port.out.TransactionPort;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Transaction Spring; JdbcTemplate của mọi adapter bên trong tham gia cùng transaction. */
@Component
public class SpringTransactionAdapter implements TransactionPort {

    private final TransactionTemplate tx;

    public SpringTransactionAdapter(TransactionTemplate tx) {
        this.tx = tx;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }
}
