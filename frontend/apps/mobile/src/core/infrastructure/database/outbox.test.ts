import { beforeEach, describe, expect, it } from 'vitest';
import { Q, type Database } from '@nozbe/watermelondb';
import { createTestDatabase } from '../../../test/createTestDatabase';
import { Outbox } from './models';

/**
 * Kiểm tra bảng outbox lưu và đọc đúng trên WatermelonDB (LokiJS trong bộ nhớ). Đây mới là
 * test CRUD của schema ở giai đoạn scaffold; logic enqueue và worker sync sẽ có test riêng.
 *
 * Payload mẫu là một lô đáp án thô của Daily Challenge: client chỉ gửi đáp án kèm UUID do
 * client sinh, server tự chấm và tính lại SM-2, Star, streak (BR-23).
 */
describe('outbox (LokiJS in-memory)', () => {
  let database: Database;

  beforeEach(() => {
    database = createTestDatabase();
  });

  async function enqueueAnswers(userId: string, batchId: string): Promise<Outbox> {
    let created!: Outbox;
    await database.write(async () => {
      created = await database.get<Outbox>('outbox').create((o) => {
        o.userId = userId;
        o.entityTable = 'challenge_session';
        o.entityId = 'session-1';
        o.operation = 'create';
        o.payload = {
          batch_id: batchId,
          session_id: 'session-1',
          answers: [
            { answer_id: 'answer-1', item_id: 'item-1', selected_option: 'B', answered_at: '2026-10-30T08:00:00Z' },
            { answer_id: 'answer-2', item_id: 'item-2', selected_option: 'D', answered_at: '2026-10-30T08:00:20Z' },
          ],
        };
        o.status = 'pending';
        o.retryCount = 0;
      });
    });
    return created;
  }

  it('lưu lô đáp án thô ở trạng thái pending', async () => {
    await enqueueAnswers('user-1', 'batch-1');

    const [row] = await database.get<Outbox>('outbox').query().fetch();
    expect(row?.status).toBe('pending');
    expect(row?.userId).toBe('user-1');
    expect(row?.payload).toMatchObject({ batch_id: 'batch-1', session_id: 'session-1' });
    expect(row?.payload).not.toHaveProperty('interval_days');
    expect(row?.payload).not.toHaveProperty('status');
  });

  it('chỉ lấy dòng pending của user đang đăng nhập', async () => {
    await enqueueAnswers('user-1', 'batch-1');
    await enqueueAnswers('user-2', 'batch-2');

    const rows = await database
      .get<Outbox>('outbox')
      .query(Q.where('user_id', 'user-1'), Q.where('status', 'pending'))
      .fetch();
    expect(rows).toHaveLength(1);
    expect(rows[0]?.payload).toMatchObject({ batch_id: 'batch-1' });
  });

  it('đánh dấu synced sau khi server xác nhận', async () => {
    const row = await enqueueAnswers('user-1', 'batch-1');

    await database.write(async () => {
      await row.update((o) => {
        o.status = 'synced';
        o.syncedAt = new Date();
      });
    });

    const updated = await database.get<Outbox>('outbox').find(row.id);
    expect(updated.status).toBe('synced');
    expect(updated.syncedAt).not.toBeNull();
  });

  it('tăng retry_count và lưu error_message khi gửi thất bại', async () => {
    const row = await enqueueAnswers('user-1', 'batch-1');

    await database.write(async () => {
      await row.update((o) => {
        o.status = 'failed';
        o.retryCount = o.retryCount + 1;
        o.errorMessage = 'Network timeout';
      });
    });

    const updated = await database.get<Outbox>('outbox').find(row.id);
    expect(updated.status).toBe('failed');
    expect(updated.retryCount).toBe(1);
    expect(updated.errorMessage).toBe('Network timeout');
  });
});
