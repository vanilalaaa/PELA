import React from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../../core/ui/theme';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import {
  Button,
  ChatBubble,
  DividerText,
  InlineAlert,
  Logo,
  Mascot,
  Screen,
  ScreenHeader,
  SocialButton,
  Tooltip,
} from '../../../core/ui/components';
import { Caption, Row, Section } from './Section';

/** DevUiKit · Component cho màn Auth (P3 U3 đợt 1). Chỉ có trong bản dev. */
export function AuthKitPage() {
  const { t } = useTranslation();
  const theme = useTheme();
  return (
    <Screen edges={['bottom']} contentStyle={{ gap: theme.spacing.xl }}>
      <Section title="ScreenHeader">
        <ScreenHeader backLabel={t('auth.back')} onBack={() => {}} />
      </Section>

      <Section title="Logo · Mascot · ChatBubble">
        <Row>
          <Logo width={120} accessibilityLabel={t('auth.logo')} />
          <Mascot width={100} />
        </Row>
        <ChatBubble message={t('auth.verify.bubble')} />
        <ChatBubble speaker={t('uiKit.mascotName')} message={t('auth.verify.bubble')} />
      </Section>

      <Section title="SocialButton">
        <View style={{ flexDirection: 'row', gap: theme.spacing['2sm'] }}>
          <SocialButton provider="google" label="Google" onPress={() => {}} />
          <SocialButton provider="google" label="Google" disabled />
        </View>
      </Section>

      <Section title="DividerText">
        <DividerText label={t('auth.orContinueWith')} />
      </Section>

      <Section title="InlineAlert">
        <Caption>warn</Caption>
        <InlineAlert message={t('auth.error.invalidCredentials')} />
        <Caption>warn + action</Caption>
        <InlineAlert
          message={t('auth.error.emailTaken')}
          action={<Button label={t('auth.login')} size="sm" variant="warn" onPress={() => {}} />}
        />
        <Caption>warn + detail</Caption>
        <InlineAlert message={t('auth.error.locked')} detail={t('auth.error.lockedUntil', { time: '21:45' })} />
        <Caption>info</Caption>
        <InlineAlert tone="info" message={t('auth.verify.spamHint')} />
      </Section>

      <Section title="Button · warn">
        <Row>
          <Button label={t('auth.login')} size="sm" variant="warn" onPress={() => {}} />
          <Button label={t('auth.login')} size="md" variant="warn" onPress={() => {}} />
          <Button label={t('auth.login')} size="sm" variant="warn" disabled />
        </Row>
      </Section>

      <Section title="Tooltip">
        <Row>
          <Tooltip text={t('auth.rememberTooltip')} accessibilityLabel={t('auth.rememberInfo')}>
            <PelaIcon name="Info" size={20} state="inactive" />
          </Tooltip>
          <Tooltip text={t('auth.rememberTooltip')} accessibilityLabel={t('auth.rememberInfo')}>
            <UIIcon name="Exclamation" size={20} />
          </Tooltip>
        </Row>
      </Section>
    </Screen>
  );
}
