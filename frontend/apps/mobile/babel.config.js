// Bật cú pháp decorator (@field, @date, @json…) cho model WatermelonDB; babel-preset-expo
// mặc định không parse được decorator. TypeScript phải được xử lý trước decorator, nếu không
// field khai báo `!` (vd. `lessonId!: string`) làm Babel báo lỗi. Giữ cùng thứ tự với
// vitest.config.ts để test biên dịch giống app.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      ['@babel/plugin-transform-typescript', { allowDeclareFields: true }],
      ['@babel/plugin-proposal-decorators', { legacy: true }],
    ],
  };
};
