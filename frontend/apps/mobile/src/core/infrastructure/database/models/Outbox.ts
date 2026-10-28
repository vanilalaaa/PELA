import { Model } from '@nozbe/watermelondb';
import { date, field, json } from '@nozbe/watermelondb/decorators';

const passthroughSanitizer = (raw: unknown) => (raw && typeof raw === 'object' ? raw : {});

export type OutboxOperation = 'create' | 'update' | 'delete';
export type OutboxStatus = 'pending' | 'syncing' | 'synced' | 'failed';

/**
 * Hàng đợi ghi offline chung (AGENTS.md mục 5 — "mọi write offline phải đi qua outbox").
 * Mọi thay đổi ở bảng offline khác (review_card, lesson_progress, writing_draft, ...) khi
 * app offline phải tạo kèm 1 dòng Outbox; khi có mạng, 1 worker đọc các dòng `pending`,
 * gửi lên API theo id client-generated (idempotent) rồi đánh dấu `synced`.
 */
export class Outbox extends Model {
  static table = 'outbox';

  @field('entity_table') entityTable!: string;
  @field('entity_id') entityId!: string;
  @field('operation') operation!: OutboxOperation;
  @json('payload', passthroughSanitizer) payload!: Record<string, unknown>;
  @field('status') status!: OutboxStatus;
  @field('retry_count') retryCount!: number;
  @date('created_at') createdAt!: Date;
  @date('synced_at') syncedAt!: Date | null;
  @field('error_message') errorMessage!: string | null;
}
