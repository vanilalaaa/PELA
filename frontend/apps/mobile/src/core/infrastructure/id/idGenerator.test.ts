import { randomUUID } from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { createIdGenerator, isUuidV4 } from './idGenerator';

describe('IdGenerator', () => {
  it('sinh UUID v4 chữ thường, không trùng', () => {
    const gen = createIdGenerator(randomUUID);
    const ids = Array.from({ length: 1000 }, () => gen.newId());
    expect(ids.every(isUuidV4)).toBe(true);
    expect(new Set(ids).size).toBe(ids.length);
  });
  it('chuẩn hoá chữ hoa về chữ thường', () => {
    const gen = createIdGenerator(() => 'A1B2C3D4-E5F6-4A7B-8C9D-0E1F2A3B4C5D');
    expect(gen.newId()).toBe('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d');
  });
  it('từ chối kết quả sai dạng', () => {
    expect(() => createIdGenerator(() => 'not-a-uuid').newId()).toThrow();
    expect(() => createIdGenerator(() => 'a1b2c3d4-e5f6-1a7b-8c9d-0e1f2a3b4c5d').newId()).toThrow();
  });
});
