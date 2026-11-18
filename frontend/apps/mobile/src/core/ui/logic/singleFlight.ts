/**
 * Bọc một tác vụ bất đồng bộ để không chạy chồng: lời gọi trong lúc lần trước chưa xong trả về
 * chính promise đang chạy (vd. bấm "Đăng nhập" hai lần, hoặc Enter rồi bấm nút).
 */
export function createSingleFlight() {
  let running: Promise<void> | null = null;
  return (task: () => Promise<void>): Promise<void> => {
    if (!running) {
      running = task().finally(() => {
        running = null;
      });
    }
    return running;
  };
}
