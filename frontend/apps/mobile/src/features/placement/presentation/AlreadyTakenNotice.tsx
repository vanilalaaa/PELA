import React from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { Button, InlineAlert } from '../../../core/ui/components';

/** 409 PLACEMENT_ALREADY_TAKEN (BR-19): báo đã làm bài, nút vào app với trạng thái lấy lại từ server. */
export function AlreadyTakenNotice({ busy, onContinue }: { busy: boolean; onContinue: () => void }) {
  const { t } = useTranslation();
  return (
    <View style={{ gap: 10 }}>
      <InlineAlert tone="info" message={t('placement.alreadyTaken')} testID="placement-already-taken" />
      <Button label={t('placement.continueToApp')} size="lg" fullWidth loading={busy} onPress={onContinue} testID="placement-continue" />
    </View>
  );
}
