/**
 * Đồng hồ Placement (FLOW-10): thời gian còn lại tính từ `deadline` của server, bù lệch giờ thiết bị
 * bằng `serverTime` lúc nhận lượt. Không phụ thuộc giờ máy có đúng hay không.
 */
export const WARNING_MS = 60_000;

/** Độ lệch server − thiết bị (ms) đo lúc nhận lượt. */
export function clockOffsetMs(serverTimeIso: string, deviceNowAtReceive: number): number {
  const server = Date.parse(serverTimeIso);
  return Number.isNaN(server) ? 0 : server - deviceNowAtReceive;
}

/** Thời gian còn lại (ms, ≥ 0) theo giờ server ước tính = giờ thiết bị + độ lệch. */
export function remainingMs(deadlineIso: string, offsetMs: number, deviceNow: number): number {
  const deadline = Date.parse(deadlineIso);
  if (Number.isNaN(deadline)) return 0;
  return Math.max(0, deadline - (deviceNow + offsetMs));
}

/** Tổng thời gian làm bài (giây) = deadline − serverTime (hiện ở 11, không ghi cứng). */
export function durationSec(deadlineIso: string, serverTimeIso: string): number {
  const d = Date.parse(deadlineIso);
  const s = Date.parse(serverTimeIso);
  return Number.isNaN(d) || Number.isNaN(s) ? 0 : Math.max(0, Math.round((d - s) / 1000));
}

/** mm:ss (làm tròn lên giây để không hiện 00:00 khi còn vài trăm ms). */
export function formatMmSs(ms: number): string {
  const total = Math.ceil(Math.max(0, ms) / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export function isWarning(ms: number): boolean {
  return ms > 0 && ms <= WARNING_MS;
}
