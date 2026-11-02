import React, { useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { Q, type Database } from '@nozbe/watermelondb';
import { useTheme } from '../../core/ui/theme';
import { Button } from '../../core/ui/components';
import type { ReviewCard } from '../../core/infrastructure/database';
import { getDatabase } from '../container';

/**
 * Màn dev kiểm tra WatermelonDB trên máy thật (ADR 0004): ghi một `review_card` mẫu, đọc lại
 * và so từng field; tắt hẳn app rồi mở lại để xác nhận dữ liệu còn lưu. Chỉ ghi dữ liệu cục bộ,
 * không tạo outbox, không gọi server. Chỉ được đăng ký vào navigator khi `__DEV__`.
 */

const CARD_ID = 'dev-check-card';

const EXPECTED = {
  user_id: 'dev-user',
  vocab_id: 'dev-vocab',
  easiness_factor: 2.18,
  interval_days: 6,
  repetition_count: 2,
  error_count: 1,
  next_review_date: '2026-11-05',
  last_reviewed_at: Date.UTC(2026, 9, 30, 8, 0, 0),
  status: 'review',
  consecutive_incorrect_count: 0,
  needs_reinforcement: true,
  origin: 'topic',
  in_pool: true,
  created_at: Date.UTC(2026, 9, 1, 0, 0, 0),
  updated_at: Date.UTC(2026, 9, 30, 8, 0, 0),
} as const;

type FieldName = keyof typeof EXPECTED;
type Row = { field: FieldName; actual: string; ok: boolean };
type Status = { kind: 'idle' } | { kind: 'written' } | { kind: 'deleted' } | { kind: 'missing' } | { kind: 'error'; message: string };

function readFields(card: ReviewCard): Record<FieldName, unknown> {
  return {
    user_id: card.userId,
    vocab_id: card.vocabId,
    easiness_factor: card.easinessFactor,
    interval_days: card.intervalDays,
    repetition_count: card.repetitionCount,
    error_count: card.errorCount,
    next_review_date: card.nextReviewDate,
    last_reviewed_at: card.lastReviewedAt?.getTime() ?? null,
    status: card.status,
    consecutive_incorrect_count: card.consecutiveIncorrectCount,
    needs_reinforcement: card.needsReinforcement,
    origin: card.origin,
    in_pool: card.inPool,
    created_at: card.createdAt?.getTime() ?? null,
    updated_at: card.updatedAt?.getTime() ?? null,
  };
}

async function findCard(database: Database): Promise<ReviewCard | null> {
  const [card] = await database.get<ReviewCard>('review_card').query(Q.where('id', CARD_ID)).fetch();
  return card ?? null;
}

export function DbCheckScreen() {
  const { t } = useTranslation();
  const theme = useTheme();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  const run = (action: () => Promise<void>) => () => {
    action().catch((error: unknown) => {
      setStatus({ kind: 'error', message: error instanceof Error ? error.message : String(error) });
    });
  };

  const write = run(async () => {
    const database = await getDatabase();
    await database.write(async () => {
      const existing = await findCard(database);
      if (existing) await existing.destroyPermanently();
      await database.get<ReviewCard>('review_card').create((card) => {
        card._raw.id = CARD_ID;
        card.userId = EXPECTED.user_id;
        card.vocabId = EXPECTED.vocab_id;
        card.easinessFactor = EXPECTED.easiness_factor;
        card.intervalDays = EXPECTED.interval_days;
        card.repetitionCount = EXPECTED.repetition_count;
        card.errorCount = EXPECTED.error_count;
        card.nextReviewDate = EXPECTED.next_review_date;
        card.lastReviewedAt = new Date(EXPECTED.last_reviewed_at);
        card.status = EXPECTED.status;
        card.consecutiveIncorrectCount = EXPECTED.consecutive_incorrect_count;
        card.needsReinforcement = EXPECTED.needs_reinforcement;
        card.origin = EXPECTED.origin;
        card.inPool = EXPECTED.in_pool;
        card.createdAt = new Date(EXPECTED.created_at);
        card.updatedAt = new Date(EXPECTED.updated_at);
      });
    });
    setRows(null);
    setStatus({ kind: 'written' });
  });

  const read = run(async () => {
    const database = await getDatabase();
    const card = await findCard(database);
    if (!card) {
      setRows(null);
      setStatus({ kind: 'missing' });
      return;
    }
    const actual = readFields(card);
    setRows(
      (Object.keys(EXPECTED) as FieldName[]).map((field) => ({
        field,
        actual: JSON.stringify(actual[field]),
        ok: actual[field] === EXPECTED[field],
      })),
    );
    setStatus({ kind: 'idle' });
  });

  const remove = run(async () => {
    const database = await getDatabase();
    await database.write(async () => {
      const existing = await findCard(database);
      if (existing) await existing.destroyPermanently();
    });
    setRows(null);
    setStatus({ kind: 'deleted' });
  });

  const passed = rows !== null && rows.every((row) => row.ok);

  return (
    <ScrollView
      style={{ backgroundColor: theme.colors.background }}
      contentContainerStyle={{ padding: theme.spacing.md, gap: theme.spacing.md }}
    >
      <Text style={{ color: theme.colors.textSecondary }}>{t('dbCheck.subtitle')}</Text>

      <Button label={t('dbCheck.write')} onPress={write} />
      <Button label={t('dbCheck.read')} variant="secondary" onPress={read} />
      <Button label={t('dbCheck.delete')} variant="ghost" onPress={remove} />

      {status.kind === 'written' ? <Text style={{ color: theme.colors.textPrimary }}>{t('dbCheck.written')}</Text> : null}
      {status.kind === 'deleted' ? <Text style={{ color: theme.colors.textPrimary }}>{t('dbCheck.deleted')}</Text> : null}
      {status.kind === 'missing' ? <Text style={{ color: theme.colors.danger }}>{t('dbCheck.missing')}</Text> : null}
      {status.kind === 'error' ? (
        <Text style={{ color: theme.colors.danger }}>{t('dbCheck.error', { message: status.message })}</Text>
      ) : null}

      {rows ? (
        <View style={{ gap: theme.spacing.xs }}>
          <Text
            style={{
              color: passed ? theme.colors.success : theme.colors.danger,
              fontSize: theme.typography.size.lg,
              fontWeight: '700',
            }}
          >
            {passed ? t('dbCheck.passed') : t('dbCheck.failed')}
          </Text>
          {rows.map((row) => (
            <Text key={row.field} style={{ color: row.ok ? theme.colors.textPrimary : theme.colors.danger }}>
              {`${row.ok ? '✓' : '✗'} ${row.field} = ${row.actual}`}
            </Text>
          ))}
        </View>
      ) : null}
    </ScrollView>
  );
}
