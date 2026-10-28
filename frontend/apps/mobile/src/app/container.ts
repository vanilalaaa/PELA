/**
 * Composition root duy nhất của app mobile: nơi duy nhất khởi tạo adapter trong
 * `core/infrastructure` và `features/<module>/infrastructure` (WatermelonDB, API client,
 * Supabase…) rồi tiêm vào use case ở `features/<module>/application`.
 * Presentation chỉ nhận use case/ViewModel đã được tiêm.
 *
 * TODO: khai báo use case đầu tiên khi 1 feature cần (vd. tiêm WatermelonDB repository vào
 * application layer của feature đó). Hiện tại container chỉ chịu trách nhiệm khởi tạo phần
 * hạ tầng dùng chung (i18n, database) theo đúng nguyên tắc "1 composition root".
 */
import { createSqliteDatabase } from '../core/infrastructure/database';
import type { Database } from '@nozbe/watermelondb';

let databasePromise: Promise<Database> | null = null;

/**
 * Lazy-init WatermelonDB (SQLite, dùng được trên dev build/device thật — KHÔNG hoạt động
 * trong Expo Go vì cần native module). Dùng Promise cache để toàn app chỉ có 1 Database.
 */
export function getDatabase(): Promise<Database> {
  if (!databasePromise) {
    databasePromise = createSqliteDatabase();
  }
  return databasePromise;
}
