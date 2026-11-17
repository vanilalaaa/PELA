import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useNavigation } from '@react-navigation/native';
import { useTheme } from '../../core/ui/theme';
import {
  BottomSheet,
  Button,
  Card,
  ProgressBar,
  QuestionOption,
  StarRating,
  type QuestionOptionState,
} from '../../core/ui/components';

/**
 * Màn "Storybook" nội bộ, chỉ có trong bản dev. Mục lục các trang con theo đợt (đợt 1: hành động
 * và nhập liệu); các component chưa làm lại theo Figma vẫn hiển thị ở đây.
 * Trạng thái "pressed" chỉ thấy khi đang chạm.
 *
 * Chỉ được đăng ký vào navigator khi `__DEV__` (xem RootNavigator.tsx).
 */
export function UiKitScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [sheetVisible, setSheetVisible] = useState(false);
  const navigation = useNavigation();
  const [selectedOption, setSelectedOption] = useState<QuestionOptionState>('default');

  const questionStates: QuestionOptionState[] = ['default', 'selected', 'correct', 'incorrect', 'disabled'];

  return (
    <ScrollView
      style={{ backgroundColor: theme.colors.bgCanvas }}
      contentContainerStyle={{ padding: theme.spacing.md, gap: theme.spacing.lg }}
    >
      <View>
        <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h2 }}>
          {t('uiKit.title')}
        </Text>
        <Text style={{ color: theme.colors.textSecondary, marginTop: theme.spacing.xs }}>{t('uiKit.subtitle')}</Text>
      </View>

      <View style={{ gap: theme.spacing.sm }}>
        <Button label={t('uiKit.openActions')} variant="soft" fullWidth onPress={() => navigation.navigate('DevUiKitActions')} />
      </View>

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
          <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h3 }}>{t('uiKit.sheetTitle')}</Text>
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
      <Text style={{ color: theme.colors.textPrimary, ...theme.typeScale.h3 }}>
        {title}
      </Text>
      {children}
    </View>
  );
}
