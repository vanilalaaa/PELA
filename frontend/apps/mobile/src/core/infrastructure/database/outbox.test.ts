import { beforeEach, describe, expect, it } from 'vitest';
import type { Database } from '@nozbe/watermelondb';
import { createInMemoryTestDatabase } from './adapters/createDatabase';
import { Outbox, ReviewCard } from './models';

/**
 * Test outbox (task 18) — dùng LokiJS adapter CHẠY IN-MEMORY (không cần SQLite/device thật),
 * đúng yêu cầu "viết 1 test outbox dùng LokiJS adapter chạy in-memory".
 *
 * Kịch bản: khi offline sửa review_card (vd. sau khi chấm 1 câu Challenge), app phải ghi
 * đồng thời 1 dòng outbox pending (AGENTS.md mục 5 — "mọi write offline đi qua outbox").
 * Mỗi test tạo lại 1 Database LokiJS mới (fresh in-memory) để các test độc lập với nhau.
 */
describe('outbox (LokiJS in-memory)', () => {
  let database: Database;

  beforeEach(async () => {
    database = await createInMemoryTestDatabase(`pela_test_${Date.now()}_${Math.random()}`);
  });

  it('tạo dòng outbox pending khi ghi offline 1 review_card', async () => {
    const userId = 'user-1';
    const vocabId = 'vocab-1';

    await database.write(async () => {
      const card = await database.get<ReviewCard>('review_card').create((r) => {
        r._raw.id = 'card-1'; // id client-generated = id dùng khi sync lên server (idempotent).
        r.userId = userId;
        r.vocabId = vocabId;
        r.easinessFactor = 2.5;
        r.intervalDays = 1;
        r.repetitionCount = 1;
        r.errorCount = 0;
        r.nextReviewDate = '2026-10-29';
        r.status = 'learning';
        r.consecutiveIncorrectCount = 0;
        r.needsReinforcement = false;
        r.origin = 'topic';
        r.inPool = true;
      });

      await database.get<Outbox>('outbox').create((o) => {
        o.entityTable = 'review_card';
        o.entityId = card.id;
        o.operation = 'create';
        o.payload = {
          card_id: card.id,
          user_id: userId,
          vocab_id: vocabId,
          interval_days: 1,
          status: 'learning',
        };
        o.status = 'pending';
        o.retryCount = 0;
      });
    });

    const pendingRows = await database.get<Outbox>('outbox').query().fetch();
    expect(pendingRows).toHaveLength(1);
    expect(pendingRows[0]?.status).toBe('pending');
    expect(pendingRows[0]?.entityTable).toBe('review_card');
    expect(pendingRows[0]?.payload).toMatchObject({ card_id: 'card-1' });
  });

  it('đánh dấu synced sau khi gửi lên server thành công (giả lập worker sync)', async () => {
    await database.write(async () => {
      await database.get<Outbox>('outbox').create((o) => {
        o.entityTable = 'review_card';
        o.entityId = 'card-2';
        o.operation = 'update';
        o.payload = { card_id: 'card-2', status: 'review' };
        o.status = 'pending';
        o.retryCount = 0;
      });
    });

    const [row] = await database.get<Outbox>('outbox').query().fetch();
    expect(row).toBeDefined();

    await database.write(async () => {
      await row!.update((o) => {
        o.status = 'synced';
        o.syncedAt = new Date();
      });
    });

    const updated = await database.get<Outbox>('outbox').find(row!.id);
    expect(updated.status).toBe('synced');
    expect(updated.syncedAt).not.toBeNull();
  });

  it('tăng retry_count và lưu error_message khi gửi lên server thất bại', async () => {
    let outboxId = '';

    await database.write(async () => {
      const created = await database.get<Outbox>('outbox').create((o) => {
        o.entityTable = 'review_card';
        o.entityId = 'card-3';
        o.operation = 'update';
        o.payload = { card_id: 'card-3' };
        o.status = 'pending';
        o.retryCount = 0;
      });
      outboxId = created.id;
    });

    const row = await database.get<Outbox>('outbox').find(outboxId);

    await database.write(async () => {
      await row.update((o) => {
        o.status = 'failed';
        o.retryCount = o.retryCount + 1;
        o.errorMessage = 'Network timeout';
      });
    });

    const updated = await database.get<Outbox>('outbox').find(outboxId);
    expect(updated.status).toBe('failed');
    expect(updated.retryCount).toBe(1);
    expect(updated.errorMessage).toBe('Network timeout');
  });
});
