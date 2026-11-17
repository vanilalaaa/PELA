import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { UIIcon } from '../icons';
import { IconButton } from './IconButton';
import { Input, type InputProps } from './Input';

const LOCK_ICON_SIZE = 18.33;

export type PasswordFieldProps =Omit<InputProps, 'secureTextEntry' | 'renderLeftIcon' | 'trailing' | 'paddingRight'>;

/**
 * Ô mật khẩu (Figma #2 PasswordField 575:11919): Input + icon khoá + IconButton ghost SM bên
 * phải (Eye khi đang ẩn, EyeClosed khi đang hiện). Trạng thái giống Input.
 */
export function PasswordField(props: PasswordFieldProps) {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);

  return (
    <Input
      autoCapitalize="none"
      autoCorrect={false}
      textContentType="password"
      {...props}
      secureTextEntry={!visible}
      paddingRight={8}
      // Figma vẽ icon khoá trong khung 18.33 (nhỏ hơn icon 22 của Input).
      renderLeftIcon={(color) => <UIIcon name="Lock" size={LOCK_ICON_SIZE} color={color} />}
      trailing={
        <IconButton
          variant="ghost"
          size="sm"
          disabled={props.disabled}
          accessibilityLabel={visible ? t('ui.password.hide') : t('ui.password.show')}
          onPress={() => setVisible((v) => !v)}
          renderIcon={(color, size) => <UIIcon name={visible ? 'EyeClosed' : 'Eye'} size={size} color={color} />}
        />
      }
    />
  );
}
