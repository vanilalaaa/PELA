/**
 * Sinh id phía client cho bản ghi tạo ở thiết bị (outbox, answer_id, report_id; BR-13, BR-23), để gửi lại
 * an toàn khi đồng bộ. Id do server cấp (vd. attemptId của Placement) KHÔNG sinh ở đây.
 */
export interface IdGeneratorPort {
  /** UUID v4 dạng chữ thường. */
  newId(): string;
}
