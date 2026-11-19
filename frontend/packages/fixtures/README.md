# @pela/fixtures

Test vector JSON dùng chung cho `packages/domain` (Vitest) và backend Java (JUnit).
Khoá JSON dùng `snake_case`. Khi đổi luật nghiệp vụ, sửa fixture trước rồi cho cả hai phía pass.

- `sm2-cases.json` — SM-2 (BR-26)
- `word-count-cases.json` — đếm từ Writing (UC-10): `countWords` (TS) và `TextAnalysis.countWords` (Java)
- `password-cases.json` — luật mật khẩu (Auth.Register.Validate) và mức độ mạnh: `evaluatePassword` (TS)

## `mock/` — dữ liệu giả cho Mock repository (ngoại lệ camelCase)

- `mock/<feature>.json`: dữ liệu mẫu mà `Mock<Feature>Repository` của mobile trả về khi chưa có API.
- **Ngoại lệ:** khoá dùng **camelCase** cho khớp OpenAPI và kiểu domain của client. Các file này không phải test vector và không dùng cho JUnit; quy ước `snake_case` ở trên không áp dụng.
- Không chứa dữ liệu thật hay PII; id là UUID mẫu.
