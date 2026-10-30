import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../core/ui/theme';
import {
  BottomSheet,
  Button,
  Card,
  Input,
  ProgressBar,
  QuestionOption,
  StarRating,
  type QuestionOptionState,
} from '../../core/ui/components';

/**
 * Màn "Storybook" nội bộ, chỉ có trong bản dev: liệt kê từng component ở đủ state (default,
 * pressed, disabled) và cả 2 theme, để so trực tiếp với Figma.
 * Riêng state "pressed" không demo tĩnh được vì chỉ xuất hiện khi đang chạm (xem Button.tsx).
 *
 * Chỉ được đăng ký vào navigator khi `__DEV__` (xem RootNavigator.tsx).
 */
export function UiKitScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [sheetVisible, setSheetVisible] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [selectedOption, setSelectedOption] = useState<QuestionOptionState>('default');

  const questionStates: QuestionOptionState[] = ['default', 'selected', 'correct', 'incorrect', 'disabled'];

  return (
    <ScrollView
      style={{ backgroundColor: theme.colors.background }}
      contentContainerStyle={{ padding: theme.spacing.md, gap: theme.spacing.lg }}
    >
      <View>
        <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.xl, fontWeight: '700' }}>
          {t('uiKit.title')}
        </Text>
        <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('uiKit.subtitle')}</Text>
      </View>

      <Section title={t('uiKit.sectionButton')}>
        <Button label={t('uiKit.buttonPrimary')} variant="primary" onPress={() => {}} />
        <Button label={t('uiKit.buttonSecondary')} variant="secondary" onPress={() => {}} />
        <Button label={t('uiKit.buttonGhost')} variant="ghost" onPress={() => {}} />
        <Button label={t('uiKit.buttonDisabled')} variant="primary" disabled onPress={() => {}} />
        <Button label={t('uiKit.buttonLoading')} variant="primary" loading onPress={() => {}} />
      </Section>

      <Section title={t('uiKit.sectionCard')}>
        <Card>
          <Text style={{ color: theme.colors.textPrimary }}>{t('uiKit.cardStatic')}</Text>
        </Card>
        <Card onPress={() => {}}>
          <Text style={{ color: theme.colors.textPrimary }}>{t('uiKit.cardPressable')}</Text>
        </Card>
        <Card onPress={() => {}} disabled>
          <Text style={{ color: theme.colors.textPrimary }}>{t('uiKit.cardDisabled')}</Text>
        </Card>
      </Section>

      <Section title={t('uiKit.sectionInput')}>
        <Input
          label={t('uiKit.inputLabel')}
          placeholder={t('uiKit.inputPlaceholder')}
          value={inputValue}
          onChangeText={setInputValue}
        />
        <Input label={t('uiKit.inputLabel')} value="" errorMessage={t('uiKit.inputError')} />
        <Input label={t('uiKit.inputLabel')} value={t('uiKit.inputReadonly')} disabled />
      </Section>

      <Section title={t('uiKit.sectionProgress')}>
        <ProgressBar progress={0.25} />
        <ProgressBar progress={0.6} color="warning" />
        <ProgressBar progress={1} color="success" />
      </Section>

      <Section title={t('uiKit.sectionStar')}>
        <StarRating value={0} />
        <StarRating value={3} />
        <StarRating value={6} max={6} />
      </Section>

      <Section title={t('uiKit.sectionQuestion')}>
        {questionStates.map((s) => (
          <QuestionOption
            key={s}
            label={t('uiKit.questionOption', { state: s })}
            state={s === 'disabled' ? 'disabled' : selectedOption === s ? s : 'default'}
            onPress={() => setSelectedOption(s)}
          />
        ))}
      </Section>

      <Section title={t('uiKit.sectionSheet')}>
        <Button label={t('uiKit.openSheet')} onPress={() => setSheetVisible(true)} />
        <BottomSheet visible={sheetVisible} onClose={() => setSheetVisible(false)}>
          <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.lg }}>{t('uiKit.sheetTitle')}</Text>
          <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.sm }}>
            {t('uiKit.sheetBody')}
          </Text>
          <Button label={t('common.close')} variant="ghost" onPress={() => setSheetVisible(false)} />
        </BottomSheet>
      </Section>
    </ScrollView>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  const theme = useTheme();
  return (
    <View style={{ gap: theme.spacing.sm }}>
      <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.lg, fontWeight: '600' }}>
        {title}
      </Text>
      {children}
    </View>
  );
}
