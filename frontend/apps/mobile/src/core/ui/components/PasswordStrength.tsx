import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useTranslation } from 'react-i18next';
import { evaluatePassword, type PasswordRules } from '@pela/domain';
import { useTheme } from '../theme';
import { PelaIcon } from '../icons';

export interface PasswordStrengthProps {
  password: string;
  /** Hiện danh sách luật (Figma PasswordRule 575:11963). */
  showRules?: boolean;
}

const RULE_ORDER: (keyof PasswordRules)[] = ['minLength', 'uppercase', 'lowercase', 'digit'];

/**
 * Thanh độ mạnh mật khẩu (Figma #3 PasswordStrength 575:11948): 3 đoạn cao 6, gap 4, bo full;
 * level n tô n đoạn đầu (1 coral-500 Yếu · 2 amber-500 Trung bình · 3 mint-500 Mạnh), đoạn trống
 * ink-100. Luật lấy từ `evaluatePassword` (packages/domain, Auth.Register.Validate).
 */
export function PasswordStrength({ password, showRules = true }: PasswordStrengthProps) {
  const theme = useTheme();
  const { t } = useTranslation();
  const { rules, level } = evaluatePassword(password);
  const strength = theme.componentColors.strength;
  const barColor = level === 0 ? strength.empty : strength[level].bar;
  const textColor = level === 0 ? theme.colors.textTertiary : strength[level].text;
  const requirement = t('ui.password.requirement');
  const caption = level === 0 ? requirement : `${t(`ui.password.level${level}`)} · ${requirement}`;

  return (
    <View
      style={styles.container}
      accessible
      accessibilityLabel={caption}
    >
      <View style={styles.bar}>
        {[1, 2, 3].map((segment) => (
          <View
            key={segment}
            style={[
              styles.segment,
              { borderRadius: theme.radii.full, backgroundColor: segment <= level ? barColor : strength.empty },
            ]}
          />
        ))}
      </View>
      <Text style={[theme.typeScale.captionBold, { color: textColor }]}>{caption}</Text>
      {showRules ? (
        <View style={styles.rules}>
          {RULE_ORDER.map((rule) => {
            const met = rules[rule];
            return (
              <View key={rule} style={styles.ruleRow} accessibilityState={{ checked: met }}>
                <PelaIcon name="Success" size={16} state={met ? 'default' : 'inactive'} />
                <Text
                  style={[
                    styles.ruleText,
                    theme.typeScale.captionBold,
                    { color: met ? theme.colors.successFg : theme.colors.textSecondary },
                  ]}
                >
                  {t(`ui.password.rule.${rule}`)}
                </Text>
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { width: '100%', gap: 6 },
  bar: { flexDirection: 'row', gap: 4 },
  segment: { flex: 1, height: 6 },
  rules: { gap: 4, marginTop: 2 },
  ruleRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  ruleText: { flex: 1 },
});
