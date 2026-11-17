/**
 * Đọc fixture mock từ packages/fixtures/mock (JSON, khoá camelCase). JSON import vào TS chỉ có kiểu
 * nới rộng (string thay cho union), nên ép kiểu tại đây — một chỗ duy nhất. Hình dạng fixture được
 * kiểm bằng test của Mock repository (bước e).
 */
export function fixture<T>(json: unknown): T {
  return json as T;
}

/** Bản sao sâu để mock trả dữ liệu mới mỗi lần (UI không vô tình sửa fixture dùng chung). */
export function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}
