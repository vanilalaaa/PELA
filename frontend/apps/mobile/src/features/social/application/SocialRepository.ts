import type { FriendRequest, ReportUserInput, UserCard } from './types';

export interface SocialRepository {
  listFriends(): Promise<UserCard[]>;
  listRequests(): Promise<FriendRequest[]>;
  searchUsers(query: string): Promise<UserCard[]>;
  sendRequest(userId: string): Promise<void>;
  respondRequest(friendshipId: string, accept: boolean): Promise<void>;
  removeFriend(userId: string): Promise<void>;
  listBlocked(): Promise<UserCard[]>;
  block(userId: string): Promise<void>;
  unblock(userId: string): Promise<void>;
  report(input: ReportUserInput): Promise<void>;
}
