/**
 * Composition root duy nhất của app mobile: nơi duy nhất khởi tạo adapter trong
 * `core/infrastructure` và `features/<module>/infrastructure` (WatermelonDB, API client,
 * Supabase…) rồi tiêm vào use case ở `features/<module>/application`.
 * Presentation chỉ nhận use case/ViewModel đã được tiêm.
 *
 * TODO: khai báo container khi có use case đầu tiên.
 */
export {};
