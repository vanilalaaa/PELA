import { describe, expect, it } from 'vitest';
import { FEATURES, dataSources, resolveDataSource } from './dataSources';

const URL = 'http://h/v1';

describe('resolveDataSource', () => {
  it('theo bảng khi có backend và không ép mock', () => {
    expect(resolveDataSource('auth', { forced: undefined, apiBaseUrl: URL })).toBe('api');
    expect(resolveDataSource('roadmap', { forced: undefined, apiBaseUrl: URL })).toBe('mock');
  });
  it('EXPO_PUBLIC_DATA_SOURCE=mock ép mọi tính năng dùng mock', () => {
    for (const f of FEATURES) expect(resolveDataSource(f, { forced: ' MOCK ', apiBaseUrl: URL })).toBe('mock');
  });
  it('chưa có base URL → mock', () => {
    expect(resolveDataSource('writing', { forced: undefined, apiBaseUrl: null })).toBe('mock');
  });
  it('chỉ auth và writing là api (có endpoint trong OpenAPI)', () => {
    expect(FEATURES.filter((f) => dataSources[f] === 'api').sort()).toEqual(['auth', 'writing']);
    expect(FEATURES).toHaveLength(15);
  });
});
