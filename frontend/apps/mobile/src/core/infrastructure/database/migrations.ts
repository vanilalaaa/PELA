import { schemaMigrations } from '@nozbe/watermelondb/Schema/migrations';

/**
 * Migration của database local. Khi đổi `schema.ts`: tăng `schema.version` lên N và thêm
 * `{ toVersion: N, steps: [...] }` vào đầu mảng (mới nhất trước), để máy đã cài bản cũ nâng
 * cấp mà không mất dữ liệu chưa đồng bộ trong outbox.
 */
export const migrations = schemaMigrations({
  migrations: [],
});
