import { Database } from '@nozbe/watermelondb';
import type { DatabaseAdapter } from '@nozbe/watermelondb/adapters/type';
import { schema } from '../schema';
import { modelClasses } from '../models';

/**
 * Factory dùng chung: nhận adapter đã khởi tạo (SQLite cho app thật, LokiJS cho test) và
 * trả về 1 Database — để schema/model không bị lặp code setup giữa 2 môi trường.
 */
export function createDatabase(adapter: DatabaseAdapter): Database {
  return new Database({ adapter, modelClasses });
}

/**
 * Adapter thật dùng trên device/dev-build (JSI SQLite). KHÔNG dùng được trong Expo Go
 * (cần dev client — đã có `expo-dev-client` trong package.json).
 * Factory riêng (không gọi ở module scope) để file này import được trong môi trường Node/test
 * (vitest) mà không kéo theo native binding của adapter SQLite.
 */
export async function createSqliteDatabase(dbName = 'pela'): Promise<Database> {
  const { default: SQLiteAdapter } = await import('@nozbe/watermelondb/adapters/sqlite');
  const adapter = new SQLiteAdapter({
    schema,
    dbName,
    jsi: true,
    onSetUpError: (error) => {
      // eslint-disable-next-line no-console
      console.error('[WatermelonDB] Lỗi khởi tạo SQLiteAdapter:', error);
    },
  });
  return createDatabase(adapter);
}

/**
 * Adapter in-memory dùng cho unit test (vd. outbox.test.ts) — không cần native module,
 * chạy được trong Node (vitest) theo đúng yêu cầu task 18 ("viết test outbox dùng LokiJS").
 */
export async function createInMemoryTestDatabase(dbName = 'pela_test'): Promise<Database> {
  const { default: LokiJSAdapter } = await import('@nozbe/watermelondb/adapters/lokijs');
  const adapter = new LokiJSAdapter({
    schema,
    dbName,
    useWebWorker: false,
    useIncrementalIndexedDB: false,
    onQuotaExceededError: (error) => {
      // eslint-disable-next-line no-console
      console.error('[WatermelonDB] Lỗi quota LokiJS:', error);
    },
    onSetUpError: (error) => {
      // eslint-disable-next-line no-console
      console.error('[WatermelonDB] Lỗi khởi tạo LokiJSAdapter:', error);
    },
  });
  return createDatabase(adapter);
}
