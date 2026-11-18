import type { ProfileRepository } from './ProfileRepository';

export type ProfileUseCases = ProfileRepository;

export function createProfileUseCases(repo: ProfileRepository): ProfileUseCases {
  return {
    getProfile: () => repo.getProfile(),
    updateFullName: (name) => repo.updateFullName(name.trim()),
    updateUsername: (username) => repo.updateUsername(username.trim()),
  };
}
