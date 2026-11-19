import type { RankTier } from '../../../core/ui/components';
import type { CefrLevel } from '../../../domain/types';

/** Bậc huy chương theo CEFR (FLOW-16: 6 tên bậc ứng A1–C2). */
export const TIER_OF_LEVEL: Record<CefrLevel, RankTier> = { A1: 1, A2: 2, B1: 3, B2: 4, C1: 5, C2: 6 };

/** Chữ cái đầu đáp án 09.1 (Figma AnswerOption). */
export const OPTION_KEYS = ['A', 'B', 'C', 'D'] as const;
