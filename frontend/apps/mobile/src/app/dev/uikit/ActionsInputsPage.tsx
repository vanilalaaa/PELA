import React, { useState } from 'react';
import { } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../../core/ui/theme';
import { UIIcon } from '../../../core/ui/icons';
import {
  Button,
  Checkbox,
  IconButton,
  Input,
  OtpInput,
  PasswordField,
  PasswordStrength,
  Radio,
  Select,
  Switch,
  type ButtonSize,
  type ButtonVariant,
  type IconButtonSize,
  type IconButtonVariant,
  Screen,
} from '../../../core/ui/components';
import { Caption, Row, Section } from './Section';

const VARIANTS: ButtonVariant[] = ['primary', 'secondary', 'soft', 'ghost', 'star', 'success', 'danger', 'dangerSolid'];
const SIZES: ButtonSize[] = ['sm', 'md', 'lg'];
const ICON_VARIANTS: IconButtonVariant[] = ['soft', 'ghost', 'primary', 'white'];
const ICON_SIZES: IconButtonSize[] = ['sm', 'md', 'lg'];
type Rank = 'apprentice' | 'journeyman' | 'expert' | 'master';

/** DevUiKit · Đợt 1: hành động và nhập liệu. Chỉ có trong bản dev. Nhấn giữ để xem trạng thái Pressed. */
export function ActionsInputsPage() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [text, setText] = useState('Minh Anh');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [checked, setChecked] = useState(true);
  const [radio, setRadio] = useState<'a' | 'b'>('a');
  const [on, setOn] = useState(true);
  const [rank, setRank] = useState<Rank | null>('journeyman');
  const ranks: { value: Rank; label: string }[] = (['apprentice', 'journeyman', 'expert', 'master'] as const).map((v) => ({
    value: v,
    label: t(`uiKit.rank.${v}`),
  }));
  const demoLabel = t('uiKit.demoAction');

  return (
    <Screen edges={['bottom']} contentStyle={{ gap: theme.spacing.xl }}>
      <Section title="Button">
        {SIZES.map((size) => (
          <React.Fragment key={size}>
            <Caption>{size.toUpperCase()}</Caption>
            <Row>
              {VARIANTS.map((variant) => (
                <Button key={variant} label={variant} variant={variant} size={size} onPress={() => {}} />
              ))}
            </Row>
          </React.Fragment>
        ))}
        <Caption>Disabled · Loading · Icon</Caption>
        <Row>
          <Button label={demoLabel} disabled />
          <Button label={demoLabel} variant="dangerSolid" disabled />
          <Button label={demoLabel} loading />
          <Button
            label={demoLabel}
            onPress={() => {}}
            renderRightIcon={(color, size) => <UIIcon name="ArrowRight" size={size} color={color} />}
          />
          <Button
            label={demoLabel}
            variant="soft"
            onPress={() => {}}
            renderLeftIcon={(color, size) => <UIIcon name="Restart" size={size} color={color} />}
          />
        </Row>
        <Button label={demoLabel} size="lg" fullWidth onPress={() => {}} />
      </Section>

      <Section title="IconButton">
        {ICON_SIZES.map((size) => (
          <Row key={size}>
            {ICON_VARIANTS.map((variant) => (
              <IconButton
                key={variant}
                variant={variant}
                size={size}
                accessibilityLabel={t('common.close')}
                onPress={() => {}}
                renderIcon={(color, s) => <UIIcon name="Close" size={s} color={color} />}
              />
            ))}
            {ICON_VARIANTS.map((variant) => (
              <IconButton
                key={`${variant}-off`}
                variant={variant}
                size={size}
                disabled
                accessibilityLabel={t('common.close')}
                renderIcon={(color, s) => <UIIcon name="Close" size={s} color={color} />}
              />
            ))}
          </Row>
        ))}
      </Section>

      <Section title="Input">
        <Input
          label={t('uiKit.inputDisplayName')}
          value={text}
          onChangeText={setText}
          hint={t('uiKit.inputHint')}
          renderLeftIcon={(color, size) => <UIIcon name="At" size={size} color={color} />}
        />
        <Input
          label={t('uiKit.inputDisplayName')}
          value=""
          placeholder={t('uiKit.inputPlaceholder')}
          errorMessage={t('uiKit.inputError')}
          renderLeftIcon={(color, size) => <UIIcon name="At" size={size} color={color} />}
        />
        <Input
          label={t('uiKit.inputDisplayName')}
          value={t('uiKit.inputReadonly')}
          disabled
          renderLeftIcon={(color, size) => <UIIcon name="At" size={size} color={color} />}
        />
      </Section>

      <Section title="PasswordField · PasswordStrength">
        <PasswordField label={t('uiKit.password')} value={password} onChangeText={setPassword} />
        <PasswordStrength password={password} />
        <Caption>Level 0 · 1 · 2 · 3</Caption>
        <PasswordStrength password="" showRules={false} />
        <PasswordStrength password="pela" showRules={false} />
        <PasswordStrength password="Pela2026" showRules={false} />
        <PasswordStrength password="Pela@2026" showRules={false} />
        <PasswordField label={t('uiKit.password')} value="pela2026" errorMessage={t('uiKit.passwordWrong')} />
        <PasswordField label={t('uiKit.password')} value="pela2026" disabled />
      </Section>

      <Section title="OtpInput">
        <OtpInput value={otp} onChange={setOtp} />
        <OtpInput value="482" onChange={() => {}} />
        <OtpInput value="482916" onChange={() => {}} status="correct" />
        <OtpInput value="482916" onChange={() => {}} status="wrong" />
      </Section>

      <Section title="Checkbox · Radio · Switch">
        <Row>
          <Checkbox checked={checked} onChange={setChecked} label={t('uiKit.checkbox')} />
          <Checkbox checked disabled label={t('uiKit.checkbox')} />
          <Checkbox checked={false} disabled label={t('uiKit.checkbox')} />
        </Row>
        <Row>
          <Radio checked={radio === 'a'} onPress={() => setRadio('a')} label={t('uiKit.radioA')} />
          <Radio checked={radio === 'b'} onPress={() => setRadio('b')} label={t('uiKit.radioB')} />
          <Radio checked disabled label={t('uiKit.radioA')} />
          <Radio checked={false} disabled label={t('uiKit.radioB')} />
        </Row>
        <Row>
          <Switch value={on} onChange={setOn} label={t('uiKit.switch')} />
          <Switch value disabled label={t('uiKit.switch')} />
          <Switch value={false} disabled label={t('uiKit.switch')} />
        </Row>
      </Section>

      <Section title="Select">
        <Select label={t('uiKit.selectLabel')} options={ranks} value={rank} onChange={setRank} />
        <Select label={t('uiKit.selectLabel')} options={ranks} value="journeyman" onChange={() => {}} disabled />
      </Section>
    </Screen>
  );
}
