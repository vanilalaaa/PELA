package com.pela.listening.domain;

/**
 * Lỗi nhập Listening, gắn với dòng CSV và tên cột để admin-web tô đỏ đúng ô (UC-19 E3).
 *
 * @param clipIndex vị trí bài nghe trong preview (null = lỗi cả file, vd thiếu cột)
 * @param row       số dòng CSV (null khi bài nghe do Admin sửa trên preview, không còn dòng gốc)
 * @param column    tên cột trong mẫu (null = lỗi cả dòng / cả bài)
 */
public record ImportIssue(Integer clipIndex, Integer row, String column, String code, String message) {
}
