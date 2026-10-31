-- V4: khoá đăng nhập theo cửa sổ thời gian (Auth.Login.Lockout, SEC-5).
-- "5 lần sai liên tiếp trong 15 phút" cần biết chuỗi lần sai bắt đầu khi nào;
-- V1 chỉ có failed_login_count + locked_until nên thêm mốc bắt đầu cửa sổ.

ALTER TABLE users
    ADD COLUMN failed_login_window_started_at timestamptz;   -- lần sai đầu tiên của chuỗi hiện tại; NULL = chưa sai

INSERT INTO system_setting (setting_key, value, description) VALUES
 ('auth.lockout_max_attempts', '5',  'Auth.Login.Lockout, SEC-5: số lần đăng nhập sai liên tiếp trong cửa sổ trước khi khoá'),
 ('auth.lockout_minutes',      '15', 'Auth.Login.Lockout, SEC-5: độ dài cửa sổ đếm lần sai và thời gian khoá (phút)')
ON CONFLICT (setting_key) DO NOTHING;
