// File MỚI — cần thiết để dùng được decorator (@field, @date, @json, ...) của WatermelonDB
// trong Expo/Metro. Không có file này thì babel-preset-expo mặc định không hiểu cú pháp
// decorator và build sẽ lỗi parse. Xem package.json mobile để biết version plugin.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [['@babel/plugin-proposal-decorators', { legacy: true }]],
  };
};
