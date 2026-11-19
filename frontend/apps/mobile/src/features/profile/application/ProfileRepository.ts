import type { Profile } from './types';

export interface ProfileRepository {
  getProfile(): Promise<Profile>;
  updateFullName(fullName: string): Promise<Profile>;
  updateUsername(username: string): Promise<Profile>;
}
