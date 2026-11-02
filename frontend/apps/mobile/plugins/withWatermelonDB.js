// Config plugin cho WatermelonDB trên Android (repo dùng CNG, android/ không commit).
//
// @nozbe/watermelondb 0.28.0 dùng `BuildConfig.DEBUG` trong module native nhưng không tự bật
// `buildFeatures.buildConfig`; từ AGP 8 cờ này mặc định tắt nên module không compile được.
// Cờ chung `android.defaults.buildfeatures.buildconfig` trong gradle.properties đã bị AGP
// loại bỏ, nên plugin chỉ bật buildConfig cho đúng module `nozbe_watermelondb`.
// JSI chưa đăng ký (adapter dùng `jsi: false`), xem
// docs/decisions/0004-watermelondb-android-buildconfig.md.
const { withProjectBuildGradle } = require('expo/config-plugins');

const MARKER = '// @pela/watermelondb-buildconfig';

const SNIPPET = `
${MARKER}
subprojects { subproject ->
  if (subproject.name == 'nozbe_watermelondb') {
    subproject.plugins.withId('com.android.library') {
      subproject.android.buildFeatures.buildConfig = true
    }
  }
}
`;

function withWatermelonDB(config) {
  return withProjectBuildGradle(config, (mod) => {
    if (mod.modResults.language !== 'groovy') {
      throw new Error('withWatermelonDB: chỉ hỗ trợ android/build.gradle dạng Groovy');
    }
    if (!mod.modResults.contents.includes(MARKER)) {
      mod.modResults.contents += SNIPPET;
    }
    return mod;
  });
}

module.exports = withWatermelonDB;
