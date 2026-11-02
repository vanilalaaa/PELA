/**
 * Đếm từ cho Writing (UC-10): chuỗi chữ/số, có thể nối bằng ' ’ hoặc -.
 * Cùng quy tắc với TextAnalysis.countWords ở backend (test vector: @pela/fixtures/word-count-cases.json).
 */
const WORD = /[\p{L}\p{N}]+(?:['’-][\p{L}\p{N}]+)*/gu;

export function countWords(text: string): number {
  if (text.trim() === '') {
    return 0;
  }
  return text.match(WORD)?.length ?? 0;
}
