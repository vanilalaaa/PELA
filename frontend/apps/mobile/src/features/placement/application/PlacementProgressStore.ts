import type { PlacementProgress } from './placementProgress';

/** Nơi lưu lượt Placement đang làm trên thiết bị, theo tài khoản; nộp xong hoặc 409 thì xoá. */
export interface PlacementProgressStore {
  load(userId: string): Promise<unknown>;
  save(userId: string, progress: PlacementProgress): Promise<void>;
  clear(): Promise<void>;
}
