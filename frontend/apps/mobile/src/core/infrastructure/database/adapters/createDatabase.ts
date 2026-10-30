import { Database } from '@nozbe/watermelondb';
import type { DatabaseAdapter } from '@nozbe/watermelondb/adapters/type';
import { schema } from '../schema';
import { migrations } from '../migrations';
import { modelClasses } from '../models';

/** Dựng Database từ một adapter đã khởi tạo, dùng chung cho app (SQLite) và test (LokiJS). */
export function createDatabase(adapter: DatabaseAdapter): Database {
  return new Database({ adapter, modelClasses });
}

/**
 * Adapter SQLite dùng trên dev build/thiết bị thật (không chạy được trên Expo Go). Import
 * động để file này vẫn nạp được trong Node mà không kéo theo native binding.
 *
 * `jsi: false`: JSI của WatermelonDB chưa được đăng ký native trên Android (ADR 0004).
 */
export async function createSqliteDatabase(dbName = 'pela'): Promise<Database> {
  const { default: SQLiteAdapter } = await import('@nozbe/watermelondb/adapters/sqlite');
  const adapter = new SQLiteAdapter({
    schema,
    migrations,
    dbName,
    jsi: false,
    onSetUpError: (error) => {
      // eslint-disable-next-line no-console
      console.error('[WatermelonDB] Lỗi khởi tạo SQLiteAdapter:', error);
    },
  });
  return createDatabase(adapter);
}
