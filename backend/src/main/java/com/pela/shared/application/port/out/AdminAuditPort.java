package com.pela.shared.application.port.out;

import java.util.UUID;

/**
 * BR-35: ghi nhật ký thao tác Admin (bảng admin_action_log, chỉ thêm, không sửa/xoá). Gọi trong cùng transaction với
 * thay đổi được ghi nhận để không có thay đổi nào thiếu nhật ký.
 *
 * @param detail object JSON hoá được, thường là {@code {"before": .., "after": ..}}; không chứa PII
 */
public interface AdminAuditPort {

    void record(UUID adminId, String actionType, String targetEntity, String targetId, Object detail);
}
