/**
 * Ranh giới tầng frontend (xem CONTRIBUTING.md).
 * Cấu trúc mỗi app (chia theo feature):
 *   src/features/<module>/{application,infrastructure,presentation}
 *   src/core/infrastructure   hạ tầng dùng chung
 *   src/domain                logic riêng của app
 *   src/app/container.ts      composition root (mobile)
 * packages/domain được coi là tầng domain.
 */
const DOMAIN = '^(packages/domain/src|apps/[^/]+/src/domain)/';
const INFRASTRUCTURE = '^apps/[^/]+/src/(core/infrastructure|features/[^/]+/infrastructure)/';

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'domain-is-pure',
      comment:
        'Domain là TypeScript thuần: không import thư viện ngoài (React, React Native, Expo, WatermelonDB, Supabase...) và không import tầng khác. Riêng file test được dùng vitest và fixtures.',
      severity: 'error',
      from: { path: DOMAIN, pathNot: '\\.test\\.ts$' },
      to: { pathNot: DOMAIN },
    },
    {
      name: 'application-no-infrastructure-presentation-react',
      comment: 'Application không import infrastructure, presentation hay React.',
      severity: 'error',
      from: { path: '^apps/[^/]+/src/features/[^/]+/application/' },
      to: {
        path: [
          INFRASTRUCTURE,
          '^apps/[^/]+/src/features/[^/]+/presentation/',
          '(^|/)node_modules/(react|react-native|react-dom)/',
        ],
      },
    },
    {
      name: 'presentation-no-infrastructure',
      comment: 'Presentation không import infrastructure; phụ thuộc được tiêm qua composition root.',
      severity: 'error',
      from: { path: '^apps/[^/]+/src/features/[^/]+/presentation/' },
      to: { path: INFRASTRUCTURE },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: '(^|/)(dist|coverage|\\.expo)/' },
    tsPreCompilationDeps: true,
    combinedDependencies: true,
  },
};
