# Việc còn lại (mobile UI)

Việc chưa làm được vì thiếu API hoặc chờ quyết định. Mỗi dòng dẫn chiếu GAP/API; xong thì xoá dòng.

| Việc | Màn | Chờ | Dẫn chiếu |
|---|---|---|---|
| Bật nút Google (đăng nhập/đăng ký bằng Google) | 01, 02, 05 | API-3 | GAP-25, CO-6 |
| Bật "Gửi lại email" ở màn xác thực và nút gửi lại trong banner "chưa xác thực" ở Login; thời gian chờ lấy từ server | 04, 05 | API-2 | GAP-28, UC-01 2.0.E3 |
| "Đổi địa chỉ email" khi tài khoản chưa xác thực: hiện chỉ quay về Đăng ký, tài khoản cũ vẫn unverified | 04 | API đổi email cho tài khoản chưa xác thực (chưa có trong OpenAPI) | GAP-30 |
| Nhánh "Đổi mật khẩu qua mã" mở từ Cài đặt (`ForgotPassword` với `source: 'settings'`): email đã che, không có ô nhập, xong về Cài đặt | 44b–44d | U12 (Cài đặt) | GAP-5 |
| Hiện thời hạn mã ở 44c và thời gian chờ gửi lại | 44c | API-20 | GAP-34, GAP-35 |
| Tách thông báo "mã đã hết hạn" khỏi "mã chưa đúng" | 44c | API-21 | GAP-7 |
| Khảo sát ra A1: hiện 15 Placed A1 trước khi vào Main (hiện vào thẳng Main) | 10 → 15 | U3 đợt 4 (Placement) | Onboarding.Survey.SkipRule |
