import React from 'react';
import { View } from 'react-native';
import { SvgXml } from 'react-native-svg';
import { thoPelaVerify } from '../brand/brandSvgs';

export type MascotPose = 'verify';

export interface MascotProps {
  /** Dáng Thỏ PELA; thêm dáng khác khi màn cần (Figma Thỏ PELA 456:1344: Wave, Reassure, Analyze…). */
  pose?: MascotPose;
  /** Chiều rộng; chiều cao theo tỉ lệ gốc của dáng. */
  width?: number;
  testID?: string;
}

const POSES = { verify: thoPelaVerify } as const;

/** Linh vật Thỏ PELA (minh hoạ, ẩn với trình đọc màn hình). Mỗi màn chỉ một mascot (Figma). */
export function Mascot({ pose = 'verify', width, testID }: MascotProps) {
  const art = POSES[pose];
  const w = width ?? art.width;
  const h = (w * art.height) / art.width;
  return (
    <View testID={testID} style={{ width: w, height: h }} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
      <SvgXml xml={art.xml} width={w} height={h} />
    </View>
  );
}
