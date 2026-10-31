/**
 * Khai báo route cho navigator gốc. Dùng React Navigation native-stack
 * (quyết định đã chốt — KHÔNG đổi sang Expo Router, xem AGENTS.md mobile).
 *
 * Mỗi feature khi có screen thật sẽ thêm route của mình vào đây (hoặc merge qua
 * một RootStackParamList riêng của feature nếu danh sách quá dài).
 */
export type RootStackParamList = {
  Home: undefined;
  // Màn dev — chỉ nên bật trong __DEV__, xem RootNavigator.tsx.
  DevUiKit: undefined;
  DevDbCheck: undefined;
};

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
