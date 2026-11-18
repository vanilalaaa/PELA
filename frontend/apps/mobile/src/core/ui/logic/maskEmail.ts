/**
 * Che email để hiển thị (44b–44d): giữ 2 ký tự đầu phần tên (tên ≤ 2 ký tự thì giữ 1), thêm "***",
 * giữ nguyên tên miền. Vd. "linh@pela.vn" → "li***@pela.vn". Chuỗi không có "@" trả nguyên (đã trim).
 */
export function maskEmail(email: string): string {
  const value = email.trim();
  const at = value.lastIndexOf('@');
  if (at <= 0) return value;
  const local = value.slice(0, at);
  const keep = local.length <= 2 ? 1 : 2;
  return `${local.slice(0, keep)}***${value.slice(at)}`;
}
