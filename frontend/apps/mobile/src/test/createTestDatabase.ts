import type { Database } from '@nozbe/watermelondb';
import LokiJSAdapter from '@nozbe/watermelondb/adapters/lokijs';
import { createDatabase } from '../core/infrastructure/database/adapters/createDatabase';
import { migrations } from '../core/infrastructure/database/migrations';
import { schema } from '../core/infrastructure/database/schema';

/**
 * Database LokiJS trong bộ nhớ cho unit test (Node, không cần native module). Chỉ import từ
 * file test để Metro không đóng gói LokiJS vào app.
 */
export function createTestDatabase(dbName = `pela_test_${Date.now()}_${Math.random()}`): Database {
  const adapter = new LokiJSAdapter({
    schema,
    migrations,
    dbName,
    useWebWorker: false,
    useIncrementalIndexedDB: false,
  });
  return createDatabase(adapter);
}
