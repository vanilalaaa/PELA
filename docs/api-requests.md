# Yêu cầu API từ phía mobile

Nơi mobile ghi những chỗ `docs/api/openapi.yaml` chưa đủ so với SRS v4.3, thay vì tự đoán endpoint. Backend đặc tả trong OpenAPI rồi đánh dấu "đã có"; mobile chỉ chuyển tính năng sang API thật (đổi `src/app/config/dataSources.ts`) khi endpoint đã có trong OpenAPI.

Trạng thái: `cần đặc tả` · `đang làm` · `đã có` · `bỏ`.

## Thiếu trong phần đã có

| Id | Chỗ | UC/BR | Cần gì | Vì sao | Trạng thái |
|---|---|---|---|---|---|
| API-1 | `GET /me` (`Me`) | UC-02 POST-1, UC-03, BR-19, Onboarding.Survey.SkipRule | Thêm `onboardingStatus: 'surveyPending' \| 'placementPending' \| 'done'` và `orientedLevel` (CEFR, có khi `placementPending`). Server chuyển trạng thái: gửi khảo sát → `placementPending` (ánh xạ A1 → `done`); nộp Placement hoặc chọn "Bắt đầu từ đầu" → `done` | Mở lại app giữa chừng phải về đúng màn: chưa khảo sát → Survey; khảo sát xong chưa thi → Placement Intro (không làm lại khảo sát, thi đúng một lần); xong → MainTabs. Hiện mock lưu trạng thái này trong secure-store | cần đặc tả |
| API-2 | Auth | UC-01 2.0.E3, Auth.Register.Verify | Endpoint gửi lại email xác minh (vd. `POST /auth/verification/resend`, công khai, giới hạn tần suất) | SRS: email chưa xác minh thì chặn đăng nhập và cho gửi lại email xác minh | cần đặc tả |
| API-3 | Auth | CO-6, GAP-8 | Đăng nhập Google (luồng OAuth qua Supabase hay qua backend, và endpoint đổi token) | SRS chỉ cho đăng nhập mạng xã hội bằng Google; OpenAPI chưa có | cần đặc tả |
| API-4 | Writing | Writing.Draft | Lưu / lấy / xoá bài nháp chủ động (hiện chỉ có nháp khi nộp bị 429 hết lượt) | Learner viết dở cần lưu nháp để viết tiếp, kể cả offline | cần đặc tả |
| API-5 | Writing | BR-30 | Bỏ qua một từ gợi ý (vd. `POST /practice/writing/vocab-suggestions/{id}/dismiss` → `status = dismissed`) | Schema đã có trạng thái `dismissed` nhưng chưa có endpoint đặt trạng thái này | cần đặc tả |
| API-19 | Lỗi `VALIDATION_FAILED` (`components.responses.ValidationFailed`) | UI-2 | Xác nhận `details[].field` dùng đúng tên thuộc tính của request body (vd. `fullName`, `email`, `password`, `confirmPassword`) | Mobile đặt lỗi dưới đúng ô theo `field`; OpenAPI chưa nói rõ quy ước tên | cần đặc tả |
| API-20 | `POST /auth/password/forgot` (202) | Auth.Recover.Request, Auth.Recover.Expire, MNT-3 | Trả `codeExpiresIn` (giây, thời hạn mã) và `resendAfter` (giây chờ trước khi gửi lại được) | 44c không hard-code "5 phút" và không đoán thời gian chờ; hiện app bỏ câu thời hạn, chỉ báo chờ khi gặp 429 (đọc `Retry-After`) | cần đặc tả |
| API-21 | `POST /auth/password/verify-otp` (422) | Auth.Recover.Expire, GAP-7 | Tách mã `OTP_EXPIRED` khỏi `INVALID_OTP`; đồng thời ghi rõ 429 có header `Retry-After` | Để báo "mã đã hết hạn, hãy gửi lại" khác "mã chưa đúng"; hiện app dùng một thông báo chung cho cả hai | cần đặc tả |

## Tính năng chưa có endpoint (mobile đang chạy mock)

Kiểu dữ liệu mobile đang dùng nằm ở `frontend/apps/mobile/src/features/<feature>/application/types.ts` (theo SRS 4.1, camelCase); fixture mẫu ở `frontend/packages/fixtures/mock/<feature>.json`. Backend có thể dùng làm điểm bắt đầu khi đặc tả.

| Id | Feature mobile | UC/BR | Thao tác cần | Trạng thái |
|---|---|---|---|---|
| API-6 | onboarding | UC-02, UC-03 A1, Onboarding.Survey/Profile | Gửi khảo sát 5 câu → trả trình độ ánh xạ; đọc trạng thái onboarding (API-1); "Bắt đầu từ đầu" → xếp A1, kết thúc onboarding (Placement.Test.StartFromBeginning) | cần đặc tả |
| API-7 | placement | UC-03, BR-19 | Lấy đề placement; nộp đáp án (id lượt do client sinh) → điểm, đạt/trượt, trình độ xếp | cần đặc tả |
| API-8 | roadmap | UC-06 | Lộ trình trình độ hiện tại: topic + lesson + tiến độ, sao | cần đặc tả |
| API-9 | lesson | UC-06, BR-36, BR-23 | Nội dung lesson (từ mới + câu hỏi); nộp đáp án thô theo lô → sao, phần sao tăng thêm | cần đặc tả |
| API-10 | finalTest | UC-07, BR-20 | Trạng thái lượt/khoá; bắt đầu lượt; nộp đáp án → điểm, trạng thái mới | cần đặc tả |
| API-11 | challenge | UC-08, UC-09, UC-21, BR-5, BR-16, BR-17 | Bộ daily/weekly hiện tại; nộp đáp án → sao | cần đặc tả |
| API-12 | listening | UC-11, BR-37 | Danh sách clip; chi tiết clip (audio URL + câu hỏi); nộp đáp án → sao, transcript | cần đặc tả |
| API-13 | profile | UC-04, BR-32 | Hồ sơ + số liệu; đổi tên hiển thị; đổi username | cần đặc tả |
| API-14 | social | UC-05, UC-26 | Bạn bè, lời mời, tìm người, kết bạn/huỷ, chặn/bỏ chặn, báo cáo người dùng | cần đặc tả |
| API-15 | gamification | UC-12, UC-13, BR-22 | Số liệu sao/streak, streak + lịch ngày học, huy hiệu + chọn hiển thị (≤ 3), bảng xếp hạng (tuần/mọi lúc, toàn bộ/bạn bè) | cần đặc tả |
| API-16 | settings | UC-27, UC-28, UC-31 | Cài đặt thông báo; hộp thư thông báo; đánh giá app; báo lỗi app | cần đặc tả |
| API-17 | sync | UC-14, BR-23 | Đẩy lô bản ghi outbox (id do client sinh, idempotent) → kết quả đồng bộ | cần đặc tả |
| API-18 | level | UC-25, BR-28, AS-12 | Tổng quan cấp hiện tại + cấp chọn được + cấu hình Level-Up Test + thời điểm được thi lại; bắt đầu Level-Up Test (id lượt do client sinh); nộp đáp án → điểm, đạt/trượt, cấp mới; xuống cấp không cần thi | cần đặc tả |
