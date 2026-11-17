import React from 'react';
import { View } from 'react-native';
import { SvgXml } from 'react-native-svg';
import type { IconLayer } from './uiIconData';

const FIGMA_BOX = 24;

interface IconLayersProps {
  layers: readonly IconLayer[];
  size: number;
  /** Màu cho `currentColor` trong SVG; bỏ trống để giữ màu gốc của SVG. */
  color?: string;
}

/**
 * Vẽ các lớp SVG xuất từ Figma đúng vị trí trong khung 24×24, rồi co giãn theo `size`.
 * Mỗi lớp giữ nguyên SVG gốc; chỉ đổi kích thước hiển thị theo tỉ lệ khung.
 */
export function IconLayers({ layers, size, color }: IconLayersProps) {
  const k = size / FIGMA_BOX;
  return (
    <View style={{ width: size, height: size }} pointerEvents="none">
      {layers.map((layer, i) => (
        <SvgXml
          key={i}
          xml={layer.xml}
          color={color}
          width={layer.w * k}
          height={layer.h * k}
          style={{ position: 'absolute', left: layer.x * k, top: layer.y * k }}
        />
      ))}
    </View>
  );
}
