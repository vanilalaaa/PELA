import { createContext } from 'react';

/** Screen cung cấp: gọi khi một ô nhập được focus để cuộn ô đó lên trên bàn phím (nếu bàn phím đang mở). */
export const KeyboardRevealContext = createContext<(() => void) | null>(null);
