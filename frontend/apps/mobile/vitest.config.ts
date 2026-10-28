import { defineConfig } from 'vitest/config';

// File MỚI — chỉ để chạy test thuần logic (outbox.test.ts) bằng LokiJS adapter trong Node,
// KHÔNG chạy UI/React Native component ở đây (cần babel/RN transform riêng, ngoài phạm vi
// task 18). environment 'node' là đủ vì WatermelonDB + LokiJS core không cần DOM/RN runtime.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
});
