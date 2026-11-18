import type { IdGeneratorPort } from '../../application/idGenerator';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

/** Đúng dạng UUID v4 (chữ thường). */
export function isUuidV4(value: string): boolean {
  return UUID_V4.test(value);
}

/**
 * Bọc một hàm randomUUID (bản thật: expo-crypto, dùng bộ sinh ngẫu nhiên của hệ điều hành). Chuẩn hoá chữ
 * thường và từ chối kết quả sai dạng thay vì ghi id hỏng.
 */
export function createIdGenerator(randomUUID: () => string): IdGeneratorPort {
  return {
    newId() {
      const id = randomUUID().toLowerCase();
      if (!isUuidV4(id)) throw new Error('randomUUID returned an invalid UUID v4');
      return id;
    },
  };
}
