import { describe, expect, it, vi } from 'vitest';
import { createSingleFlight } from './singleFlight';

describe('createSingleFlight', () => {
  it('gọi chồng khi đang chạy → chỉ chạy một lần', async () => {
    const run = createSingleFlight();
    let release!: () => void;
    const task = vi.fn(() => new Promise<void>((r) => (release = r)));
    const a = run(task);
    const b = run(task);
    release();
    await Promise.all([a, b]);
    expect(task).toHaveBeenCalledTimes(1);
  });
  it('xong (kể cả lỗi) thì lần sau chạy lại', async () => {
    const run = createSingleFlight();
    const task = vi.fn(() => Promise.reject(new Error('x')));
    await run(task).catch(() => undefined);
    await run(task).catch(() => undefined);
    expect(task).toHaveBeenCalledTimes(2);
  });
});
