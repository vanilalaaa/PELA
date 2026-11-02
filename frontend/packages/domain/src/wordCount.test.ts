import { describe, expect, it } from 'vitest';
import wordCountCases from '@pela/fixtures/word-count-cases.json';
import { countWords } from './wordCount';

describe('countWords test vectors', () => {
  for (const c of wordCountCases.cases) {
    it(c.id, () => {
      expect(countWords(c.text)).toBe(c.expected);
    });
  }
});
