import { Model } from '@nozbe/watermelondb';
import { date, field, json } from '@nozbe/watermelondb/decorators';

const passthroughSanitizer = (raw: unknown) => (raw && typeof raw === 'object' ? raw : {});

export type OutboxOperation = 'create' | 'update' | 'delete';
export type OutboxStatus = 'pending' | 'syncing' | 'synced' | 'failed';

/**
 * Hàng đợi ghi offline chung (AGENTS.md mục 5). Mỗi thao tác ghi offline tạo kèm 1 dòng
 * Outbox; khi có mạng, worker đọc các dòng `pending` của user đang đăng nhập, gửi lên API
 * kèm UUID do client sinh (gửi lại an toàn) rồi đánh dấu `synced`.
 *
 * `payload` chỉ chứa dữ liệu thô (đáp án, nội dung bài viết…), không chứa giá trị client tự
 * tính như Star, streak, interval_days hay status của review_card (BR-23).
 */
export class Outbox extends Model {
  static table = 'outbox';

  @field('user_id') userId!: string;
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
