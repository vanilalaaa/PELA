import type { SocialRepository } from './SocialRepository';
import type { ReportUserInput } from './types';

/** Lý do "khác" phải có ghi chú (SRS 4.1.34). */
export function isValidReport(input: ReportUserInput): boolean {
  return input.reason !== 'other' || (input.note?.trim().length ?? 0) > 0;
}

export interface SocialUseCases extends SocialRepository {
  isValidReport: typeof isValidReport;
}

export function createSocialUseCases(repo: SocialRepository): SocialUseCases {
  return {
    listFriends: () => repo.listFriends(),
    listRequests: () => repo.listRequests(),
    searchUsers: (q) => repo.searchUsers(q.trim()),
    sendRequest: (id) => repo.sendRequest(id),
    respondRequest: (id, accept) => repo.respondRequest(id, accept),
    removeFriend: (id) => repo.removeFriend(id),
    listBlocked: () => repo.listBlocked(),
    block: (id) => repo.block(id),
    unblock: (id) => repo.unblock(id),
    report: (input) => repo.report(input),
    isValidReport,
  };
}
