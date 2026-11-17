/** Số chữ số của mã xác thực (Auth.Recover: mã 6 số gửi qua email). */
export const OTP_LENGTH = 6;

/**
 * Chuẩn hoá chuỗi người dùng gõ hoặc dán vào ô OTP: chỉ giữ chữ số (kể cả số toàn chiều rộng
 * khi dán từ một số bàn phím), cắt về `length` ký tự.
 */
export function sanitizeOtp(text: string, length: number = OTP_LENGTH): string {
  const ascii = text.normalize('NFKC');
  return ascii.replace(/\D/g, '').slice(0, length);
}

/** Tách mã thành mảng ô (ô trống là chuỗi rỗng) để vẽ OtpCell. */
export function otpCells(code: string, length: number = OTP_LENGTH): string[] {
  return Array.from({ length }, (_, i) => code[i] ?? '');
}
