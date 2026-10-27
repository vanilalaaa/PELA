import { describe, expect, it } from 'vitest';
import sm2Cases from '@pela/fixtures/sm2-cases.json';
import { SM2_DEFAULTS } from './sm2';

describe('SM-2 test vectors', () => {
  it('fixture có 3 case hợp lệ', () => {
    expect(sm2Cases.cases).toHaveLength(3);
    for (const c of sm2Cases.cases) {
      expect(c.initial.easiness_factor).toBe(SM2_DEFAULTS.initialEasinessFactor);
      expect(c.reviews.length).toBeGreaterThan(0);
    }
  });

  for (const c of sm2Cases.cases) {
    // TODO(BR-26): đổi thành test thật khi applyReview được hiện thực
    it.todo(`${c.id}: ${c.description}`);
  }
});
