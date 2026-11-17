import React from 'react';
import { IconLayers } from './IconLayers';
import { pelaIconData, type PelaIconName, type PelaIconVariants } from './pelaIconData';

export type PelaIconState = 'default' | 'inactive';

export interface PelaIconProps {
  name: PelaIconName;
  size?: number;
  state?: PelaIconState;
}

/**
 * Icon chức năng (Figma PelaIcon 460:2118, Circle=false). Giữ nguyên màu gốc từ Figma
 * (duotone, lớp mờ 50%); không ép `currentColor`. Biến thể Inactive lấy từ Figma; icon nào
 * chưa xuất Inactive thì dùng Default.
 */
export function PelaIcon({ name, size = 24, state = 'default' }: PelaIconProps) {
  const variants: PelaIconVariants = pelaIconData[name];
  const layer = state === 'inactive' && variants.inactive ? variants.inactive : variants.default;
  return <IconLayers layers={[layer]} size={size} />;
}
