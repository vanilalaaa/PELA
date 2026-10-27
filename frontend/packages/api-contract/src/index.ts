/**
 * DTO + Zod schema sinh từ docs/api/openapi.yaml.
 * TODO: chọn công cụ sinh code từ OpenAPI và thêm script `generate`. Không sửa tay file sinh ra.
 */

/** Khuôn lỗi chung của API: {"error":{"code","message","details"}} */
export interface ApiError {
  error: {
    code: string;
    message: string;
    details?: unknown;
  };
}

export interface HealthResponse {
  status: 'ok';
}
