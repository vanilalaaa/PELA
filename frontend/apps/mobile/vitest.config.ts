import { transformAsync } from '@babel/core';
import { defineConfig, type Plugin } from 'vitest/config';

// Biên dịch file .ts trong src bằng Babel với cùng plugin decorator như Metro (babel.config.js):
// TypeScript, decorator legacy, rồi class properties. Nếu để Vite tự biên dịch, field `!` của model
// WatermelonDB thành thuộc tính instance, che accessor của decorator và dữ liệu không được ghi.
function babelLikeMetro(): Plugin {
  return {
    name: 'pela:babel-like-metro',
    enforce: 'pre',
    async transform(code, id) {
      if (!/\/src\/.*\.ts$/.test(id)) return null;
      const result = await transformAsync(code, {
        filename: id,
        babelrc: false,
        configFile: false,
        sourceMaps: true,
        plugins: [
          ['@babel/plugin-transform-typescript', { allowDeclareFields: true }],
          ['@babel/plugin-proposal-decorators', { legacy: true }],
          // babel-preset-expo làm bước này cho Metro; vitest không dùng preset đó nên tự thêm.
          ['@babel/plugin-transform-class-properties', { loose: true }],
        ],
      });
      return result?.code ? { code: result.code, map: result.map } : null;
    },
  };
}

// Chỉ chạy test logic trong Node (WatermelonDB + LokiJS không cần DOM hay runtime RN).
// Component React Native cần transform riêng nên chưa test ở đây.
export default defineConfig({
  plugins: [babelLikeMetro()],
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
