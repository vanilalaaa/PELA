import React from 'react';
import { UseCasesContext, type UseCases } from './UseCasesContext';

export function UseCasesProvider({ value, children }: { value: UseCases; children: React.ReactNode }) {
  return <UseCasesContext.Provider value={value}>{children}</UseCasesContext.Provider>;
}
