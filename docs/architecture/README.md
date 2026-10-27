# Kiến trúc

Đặt tài liệu "Thiết kế Clean Architecture" (PDF) vào thư mục này.

TODO: chưa có file tài liệu kiến trúc trong repo — nhóm bổ sung.

Tóm tắt nhanh (chi tiết xem tài liệu gốc):

- Backend: modular monolith, 16 module, mỗi module có `domain/`, `application/port/in`, `application/port/out`, `application/usecase`, `adapter/in/web`, `adapter/in/job`, `adapter/out/...`. Ranh giới được kiểm bằng ArchUnit (`backend/src/test/java/com/pela/ArchitectureTest.java`).
- Frontend: tầng `domain` → `application` → `presentation`/`infrastructure`. Ranh giới được kiểm bằng dependency-cruiser (`frontend/.dependency-cruiser.cjs`).
