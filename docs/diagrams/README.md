# Sơ đồ

File nguồn Mermaid (`.mmd`) của các sơ đồ trong SRS v4.3 ([`docs/srs/SRS_1.md`](../srs/SRS_1.md)), bản 03/11/2026.

## Cấu trúc

- `01_…17_*.mmd`: sơ đồ Phụ lục A (A-1…A-17), gồm sequence và state.
- `chuong-2-4/`: sơ đồ chương 2–4:
  - `2.1_flow_context-diagram.mmd`: context diagram (Hình 2.1)
  - `3.1_flow_use-case-diagram.mmd`: use case diagram (Hình 3.1)
  - `4.1a…4.1e_erd_*.mmd`: ERD theo 5 nhóm (Hình 4.1 a–e)

## Quy ước

- Sửa sơ đồ thì sửa file `.mmd` **và** khối Mermaid tương ứng trong `SRS_1.md`, trong cùng một PR.
- Mỗi hình trong `SRS_1.md` có dòng `Nguồn:` trỏ tới file ở đây; đổi tên file thì cập nhật dòng đó.
