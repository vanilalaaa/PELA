package com.pela.listening.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc CSV theo RFC 4180: dấu phẩy phân cách, ô có thể bọc trong dấu nháy kép ({@code ""} là một dấu nháy), ô bọc
 * nháy được chứa dấu phẩy và xuống dòng; chấp nhận LF hoặc CRLF và bỏ BOM UTF-8 ở đầu. Dòng trống bị bỏ qua.
 * Không dùng thư viện ngoài để domain thuần.
 */
public final class CsvReader {

    private CsvReader() {
    }

    /** Một bản ghi; {@code lineNumber} là số dòng vật lý (đếm từ 1) nơi bản ghi bắt đầu. */
    public record Record(int lineNumber, List<String> cells) {
    }

    public static List<Record> read(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<Record> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        int line = 1;
        int recordLine = 1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                i++;
                continue;
            }
            switch (c) {
                case '"' -> {
                    if (!cellStarted || cell.isEmpty()) {
                        quoted = true;
                        cellStarted = true;
                    } else {
                        // Dấu nháy giữa ô không bọc nháy: giữ nguyên như ký tự thường
                        cell.append(c);
                    }
                }
                case ',' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    cellStarted = true;
                }
                case '\r' -> {
                    // CRLF: xử lý ở '\n'; CR đơn lẻ coi như xuống dòng
                    if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') {
                        recordLine = endRecord(records, cells, cell, cellStarted, recordLine, ++line);
                        cellStarted = false;
                    }
                }
                case '\n' -> {
                    recordLine = endRecord(records, cells, cell, cellStarted, recordLine, ++line);
                    cellStarted = false;
                }
                default -> {
                    cell.append(c);
                    cellStarted = true;
                }
            }
            i++;
        }
        if (quoted) {
            throw new CsvFormatException(recordLine, "Unterminated quoted cell");
        }
        endRecord(records, cells, cell, cellStarted, recordLine, line);
        return records;
    }

    private static int endRecord(List<Record> records, List<String> cells, StringBuilder cell, boolean cellStarted,
            int recordLine, int nextLine) {
        if (cellStarted || !cells.isEmpty() || !cell.isEmpty()) {
            cells.add(cell.toString());
            records.add(new Record(recordLine, List.copyOf(cells)));
        }
        cells.clear();
        cell.setLength(0);
        return nextLine;
    }

    /** CSV hỏng cấu trúc (vd nháy kép không đóng). */
    public static class CsvFormatException extends RuntimeException {
        private final int lineNumber;

        public CsvFormatException(int lineNumber, String message) {
            super(message);
            this.lineNumber = lineNumber;
        }

        public int lineNumber() {
            return lineNumber;
        }
    }
}
