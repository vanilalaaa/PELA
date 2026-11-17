import { createContext, useContext } from 'react';

/**
 * Tập use case đã tiêm phụ thuộc, do container.ts dựng. Mỗi tính năng thêm khoá của mình ở bước (c),
 * vd. `auth: AuthUseCases`. Presentation chỉ lấy use case từ đây, không import infrastructure.
 */
export type UseCases = Record<string, never>;

export const UseCasesContext = createContext<UseCases | null>(null);

export function useUseCases(): UseCases {
  const value = useContext(UseCasesContext);
  if (!value) throw new Error('useUseCases must be used inside UseCasesProvider');
  return value;
}
