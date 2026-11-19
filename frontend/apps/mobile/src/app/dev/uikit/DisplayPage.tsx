import React, { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { useTheme } from '../../../core/ui/theme';
import { PelaIcon, UIIcon } from '../../../core/ui/icons';
import {
  AnswerOption,
  Avatar,
  Badge,
  BottomNav,
  Card,
  Chip,
  IconCircle,
  ProgressBar,
  StarRating,
  StatPill,
  Tabs,
  Tag,
  type AnswerOptionState,
  type AvatarSize,
  type AvatarTone,
  type BadgeTone,
  type BottomNavTab,
  type CardTone,
  type ChipTone,
  type IconCircleSize,
  type IconCircleTone,
  type ProgressTone,
  type TagTone,
  Screen,
} from '../../../core/ui/components';
import { Caption, Row, Section } from './Section';

const TAG_TONES: TagTone[] = ['blue', 'amber', 'mint', 'coral'];
const CHIP_TONES: ChipTone[] = ['neutral', 'sky', 'mint', 'amber', 'coral'];
const BADGE_TONES: BadgeTone[] = ['blue', 'coral', 'mint', 'amber', 'soft', 'neutral'];
const AVATAR_TONES: AvatarTone[] = ['blue', 'amber', 'mint', 'coral', 'lilac'];
const AVATAR_SIZES: AvatarSize[] = [32, 44, 56, 72];
const CIRCLE_TONES: IconCircleTone[] = ['blue', 'sky', 'amber', 'coral', 'mint', 'lilac', 'streak', 'neutral'];
const CIRCLE_SIZES: IconCircleSize[] = ['xs', 'sm', 'md', 'lg', 'xl'];
const CARD_TONES: CardTone[] = ['white', 'blue', 'sky', 'amber', 'coral', 'mint', 'lilac'];
const PROGRESS_TONES: ProgressTone[] = ['blue', 'amber', 'mint', 'coral', 'streak'];
const ANSWER_STATES: AnswerOptionState[] = ['idle', 'selected', 'correct', 'wrong', 'disabled'];

/** DevUiKit · Đợt 2: hiển thị. Chỉ có trong bản dev. */
export function DisplayPage() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [tags, setTags] = useState<Record<TagTone, boolean>>({ blue: true, amber: true, mint: false, coral: false });
  const [tab2, setTab2] = useState<'vocab' | 'grammar'>('vocab');
  const [tab3, setTab3] = useState<'vocab' | 'grammar' | 'listening'>('grammar');
  const [nav, setNav] = useState<BottomNavTab>('home');
  const [answer, setAnswer] = useState<string | null>('B');
  const tabItems = [
    { key: 'vocab', label: t('uiKit.tabVocab') },
    { key: 'grammar', label: t('uiKit.tabGrammar') },
    { key: 'listening', label: t('uiKit.tabListening') },
  ] as const;

  return (
    <Screen edges={['bottom']} contentStyle={{ gap: theme.spacing.xl }}>
      <Section title="Tag">
        <Row>
          {TAG_TONES.map((tone) => (
            <Tag
              key={tone}
              tone={tone}
              label={t('uiKit.tagTopic')}
              selected={tags[tone]}
              onPress={() => setTags((s) => ({ ...s, [tone]: !s[tone] }))}
              renderIcon={(sel) => <PelaIcon name="Lesson" size={16} state={sel ? 'default' : 'inactive'} />}
            />
          ))}
        </Row>
      </Section>

      <Section title="Chip">
        {(['soft', 'solid'] as const).map((appearance) =>
          (['sm', 'md'] as const).map((size) => (
            <React.Fragment key={appearance + size}>
              <Caption>{`${appearance} · ${size}`}</Caption>
              <Row>
                {CHIP_TONES.map((tone) => (
                  <Chip key={tone} tone={tone} appearance={appearance} size={size} label={t('uiKit.chipLabel')} />
                ))}
              </Row>
            </React.Fragment>
          )),
        )}
      </Section>

      <Section title="Badge">
        <Row>
          {BADGE_TONES.map((tone) => (
            <Badge key={tone} tone={tone} count={tone === 'soft' ? 120 : 3} />
          ))}
          <Badge tone="coral" />
          <Badge tone="mint" />
        </Row>
      </Section>

      <Section title="Avatar">
        {AVATAR_SIZES.map((size) => (
          <Row key={size}>
            {AVATAR_TONES.map((tone) => (
              <Avatar key={tone} name={t('uiKit.avatarName')} tone={tone} size={size} ring={tone === 'blue'} />
            ))}
          </Row>
        ))}
      </Section>

      <Section title="IconCircle">
        <Row>
          {CIRCLE_SIZES.map((size) => (
            <IconCircle key={size} size={size} renderIcon={(_c, s) => <PelaIcon name="Star" size={s} />} />
          ))}
        </Row>
        <Row>
          {CIRCLE_TONES.map((tone) => (
            <IconCircle key={tone} tone={tone} renderIcon={(c, s) => <UIIcon name="Volume" size={s} color={c} />} />
          ))}
        </Row>
      </Section>

      <Section title="Card">
        {CARD_TONES.map((tone) => (
          <Card key={tone} tone={tone} title={`${t('uiKit.cardTitle')} · ${tone}`} body={t('uiKit.cardBody')} onPress={() => {}} />
        ))}
        <Card title={t('uiKit.cardTitle')} body={t('uiKit.cardBody')} elevated={false} />
        <Card title={t('uiKit.cardTitle')} body={t('uiKit.cardBody')} onPress={() => {}} disabled />
      </Section>

      <Section title="ProgressBar">
        {PROGRESS_TONES.map((tone, i) => (
          <ProgressBar key={tone} tone={tone} progress={i * 0.25} label={i === 2 ? '3/5' : undefined} />
        ))}
      </Section>

      <Section title="StatPill">
        <Row>
          <StatPill kind="streak" value={12} />
          <StatPill kind="star" value={1250} />
          <StatPill kind="streak" value={12} label={t('uiKit.statDays')} />
        </Row>
      </Section>

      <Section title="StarRating">
        <StarRating value={0} />
        <StarRating value={3} />
        <StarRating value={6} max={6} size={20} />
      </Section>

      <Section title="Tabs">
        <Tabs items={tabItems.slice(0, 2)} value={tab2} onChange={(k) => setTab2(k as 'vocab' | 'grammar')} />
        <Tabs items={tabItems} value={tab3} onChange={setTab3} />
      </Section>

      <Section title="AnswerOption">
        {['A', 'B', 'C'].map((k) => (
          <AnswerOption
            key={k}
            keyLabel={k}
            label={t('uiKit.answerSample')}
            state={answer === k ? 'selected' : 'idle'}
            onPress={() => setAnswer(k)}
          />
        ))}
        <Caption>states</Caption>
        {ANSWER_STATES.map((s) => (
          <AnswerOption key={s} keyLabel="A" label={`${t('uiKit.answerSample')} · ${s}`} state={s} />
        ))}
      </Section>

      <Section title="BottomNav">
        <View style={{ marginHorizontal: -theme.spacing.screenGutter }}>
          <BottomNav active={nav} onChange={setNav} rankBadgeCount={2} />
        </View>
      </Section>
    </Screen>
  );
}
