/**
 * Định dạng ngày giờ tự viết (không dùng Intl: Hermes trên Android trả dạng dài, không đồng nhất).
 * Theo giờ máy. vi: "lúc HH:mm, dd/MM/yyyy" · en: "at HH:mm, MM/dd/yyyy". ISO hỏng hoặc null → null.
 */
const pad = (n: number) => String(n).padStart(2, '0');

function parse(iso: string | null): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

const isVi = (lang: string) => lang.toLowerCase().startsWith('vi');

/** Chỉ ngày: dd/MM/yyyy (vi) · MM/dd/yyyy (en). */
export function formatDate(iso: string | null, lang: string): string | null {
  const d = parse(iso);
  if (!d) return null;
  const day = pad(d.getDate());
  const month = pad(d.getMonth() + 1);
  const year = String(d.getFullYear());
  return isVi(lang) ? `${day}/${month}/${year}` : `${month}/${day}/${year}`;
}

/** Giờ và ngày: "lúc HH:mm, dd/MM/yyyy" (vi) · "at HH:mm, MM/dd/yyyy" (en). */
export function formatDateTime(iso: string | null, lang: string): string | null {
  const d = parse(iso);
  if (!d) return null;
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return `${isVi(lang) ? 'lúc' : 'at'} ${time}, ${formatDate(iso, lang)}`;
}
