import { describe, expect, it } from 'vitest';
import passwordCases from '@pela/fixtures/password-cases.json';
import { evaluatePassword } from './password';

describe('evaluatePassword test vectors', () => {
  for (const c of passwordCases.cases) {
    it(c.id, () => {
      const result = evaluatePassword(c.password);
      expect(result.valid).toBe(c.valid);
      expect(result.level).toBe(c.level);
      expect(result.rules).toEqual({
        minLength: c.rules.min_length,
        uppercase: c.rules.uppercase,
        lowercase: c.rules.lowercase,
        digit: c.rules.digit,
      });
    });
  }
});
