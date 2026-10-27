# Tài liệu PELA

| Thư mục / file | Nội dung |
|---|---|
| [`srs/`](srs/) | Đặc tả yêu cầu phần mềm (SRS v4.1) — nguồn sự thật cao nhất |
| [`architecture/`](architecture/) | Tài liệu thiết kế Clean Architecture |
| [`diagrams/`](diagrams/) | Sơ đồ Mermaid (`*.mmd`): use case, ERD, sequence, … |
| [`api/openapi.yaml`](api/openapi.yaml) | Hợp đồng REST API `/v1` (OpenAPI 3.1) |
| [`decisions/`](decisions/) | Architecture Decision Records (ADR) |

## ADR

- [0001 — Chọn Google Gemini](decisions/0001-chon-gemini.md)
- [0002 — RAG chỉ dùng cho Writing](decisions/0002-rag-chi-cho-writing.md)
- [0003 — Offline-first với bảng outbox](decisions/0003-offline-first-outbox.md)

## Thứ tự ưu tiên khi mâu thuẫn

SRS > tài liệu kiến trúc > OpenAPI > code hiện có. Gặp mâu thuẫn thì nêu ra để nhóm quyết định, không tự chọn.
