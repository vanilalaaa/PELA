/**
 * Composition root duy nhất của app mobile: nơi duy nhất được khởi tạo adapter
 * trong `infrastructure/` (WatermelonDB, API client, Supabase…) và tiêm vào use case
 * ở `application/`. Presentation chỉ nhận use case/ViewModel đã được tiêm.
 *
 * TODO: khai báo container khi có use case đầu tiên.
 */
export {};
