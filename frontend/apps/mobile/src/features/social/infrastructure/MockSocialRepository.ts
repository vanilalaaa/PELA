import socialFixture from '@pela/fixtures/mock/social.json';
import { clone, fixture, mockCall } from '../../../core/infrastructure/mock';
import type { SocialRepository } from '../application/SocialRepository';
import type { FriendRequest, UserCard } from '../application/types';

interface SocialFixture {
  friends: UserCard[];
  requests: FriendRequest[];
  blocked: UserCard[];
}

const data = fixture<SocialFixture>(socialFixture);
const done = () => mockCall(() => undefined, undefined);

export function createMockSocialRepository(): SocialRepository {
  return {
    listFriends: () => mockCall(() => clone(data.friends), []),
    listRequests: () => mockCall(() => clone(data.requests), []),
    searchUsers: (query) =>
      mockCall(() => clone(data.friends).filter((u) => `${u.fullName} ${u.username ?? ''}`.toLowerCase().includes(query.toLowerCase())), []),
    sendRequest: done,
    respondRequest: done,
    removeFriend: done,
    listBlocked: () => mockCall(() => clone(data.blocked), []),
    block: done,
    unblock: done,
    report: done,
  };
}
