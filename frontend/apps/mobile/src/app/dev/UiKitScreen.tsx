import React, { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
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
 * Màn "Storybook" nội bộ (dev-only, item 17 trong task) — liệt kê từng component ở đủ
 * state (default, pressed, disabled) và cả 2 theme, để người làm UI so trực tiếp với Figma.
 * Riêng state "pressed" không demo tĩnh được vì chỉ xuất hiện khi đang chạm (xem Button.tsx).
 *
 * Không export khỏi __DEV__ (xem RootNavigator.tsx).
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
        <Button label="Primary" variant="primary" onPress={() => {}} />
        <Button label="Secondary" variant="secondary" onPress={() => {}} />
        <Button label="Ghost" variant="ghost" onPress={() => {}} />
        <Button label="Disabled" variant="primary" disabled onPress={() => {}} />
        <Button label="Loading" variant="primary" loading onPress={() => {}} />
      </Section>

      <Section title={t('uiKit.sectionCard')}>
        <Card>
          <Text style={{ color: theme.colors.textPrimary }}>Card tĩnh (không bấm được)</Text>
        </Card>
        <Card onPress={() => {}}>
          <Text style={{ color: theme.colors.textPrimary }}>Card bấm được (có state pressed)</Text>
        </Card>
        <Card onPress={() => {}} disabled>
          <Text style={{ color: theme.colors.textPrimary }}>Card disabled</Text>
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
        <Input label={t('uiKit.inputLabel')} value="Không sửa được" disabled />
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
            label={`Option — ${s}`}
            state={s === 'default' && selectedOption === 'default' ? 'default' : s}
            onPress={() => setSelectedOption(s)}
          />
        ))}
      </Section>

      <Section title={t('uiKit.sectionSheet')}>
        <Button label={t('uiKit.openSheet')} onPress={() => setSheetVisible(true)} />
        <BottomSheet visible={sheetVisible} onClose={() => setSheetVisible(false)}>
          <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.lg }}>BottomSheet</Text>
          <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.sm }}>
            Dựng bằng Modal + Animated có sẵn trong React Native, không thêm lib native mới.
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

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  const theme = useTheme();
  return (
    <View style={styles.row}>
      <Text style={{ color: theme.colors.textPrimary, fontSize: theme.typography.size.md }}>{label}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
});
