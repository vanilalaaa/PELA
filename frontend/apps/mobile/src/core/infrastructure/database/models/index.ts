export { Vocabulary } from './Vocabulary';
export { Topic } from './Topic';
export { Lesson } from './Lesson';
export { LessonVocab } from './LessonVocab';
export { LessonProgress } from './LessonProgress';
export { TopicProgress } from './TopicProgress';
export { ReviewCard } from './ReviewCard';
export { ChallengeSession } from './ChallengeSession';
export { ChallengeItem } from './ChallengeItem';
export { ListeningClip } from './ListeningClip';
export { WritingDraft } from './WritingDraft';
export { Streak } from './Streak';
export { UserStats } from './UserStats';
export { Outbox } from './Outbox';
export type { OutboxOperation, OutboxStatus } from './Outbox';

import { Vocabulary } from './Vocabulary';
import { Topic } from './Topic';
import { Lesson } from './Lesson';
import { LessonVocab } from './LessonVocab';
import { LessonProgress } from './LessonProgress';
import { TopicProgress } from './TopicProgress';
import { ReviewCard } from './ReviewCard';
import { ChallengeSession } from './ChallengeSession';
import { ChallengeItem } from './ChallengeItem';
import { ListeningClip } from './ListeningClip';
import { WritingDraft } from './WritingDraft';
import { Streak } from './Streak';
import { UserStats } from './UserStats';
import { Outbox } from './Outbox';

/** Danh sách model truyền vào `new Database({ modelClasses, ... })`. */
export const modelClasses = [
  Vocabulary,
  Topic,
  Lesson,
  LessonVocab,
  LessonProgress,
  TopicProgress,
  ReviewCard,
  ChallengeSession,
  ChallengeItem,
  ListeningClip,
  WritingDraft,
  Streak,
  UserStats,
  Outbox,
];
