import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import * as Localization from 'expo-localization';
import en from './locales/en.json';
import vi from './locales/vi.json';

/**
 * i18n cho app mobile — vi/en, tự nhận diện ngôn ngữ máy qua expo-localization.
 * Quy ước: MỌI text hiển thị cho người dùng phải qua t('...'), không viết chuỗi cứng
 * trong component (xem AGENTS.md mục 8 — "chỉ hiển thị chuỗi qua i18next").
 */

export const SUPPORTED_LANGUAGES = ['vi', 'en'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: SupportedLanguage = 'vi';

function detectDeviceLanguage(): SupportedLanguage {
  const locales = Localization.getLocales();
  const tag = locales[0]?.languageCode ?? DEFAULT_LANGUAGE;
  return (SUPPORTED_LANGUAGES as readonly string[]).includes(tag) ? (tag as SupportedLanguage) : DEFAULT_LANGUAGE;
}

let initialized = false;

/** Gọi 1 lần ở composition root (container.ts) trước khi app render. */
export function initI18n() {
  if (initialized) return i18n;
  initialized = true;

  i18n.use(initReactI18next).init({
    resources: {
      vi: { translation: vi },
      en: { translation: en },
    },
    lng: detectDeviceLanguage(),
    fallbackLng: DEFAULT_LANGUAGE,
    interpolation: { escapeValue: false },
    compatibilityJSON: 'v4',
  });

  return i18n;
}

export { i18n };
