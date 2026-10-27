/**
 * Ranh giới tầng frontend (xem AGENTS.md).
 * Cấu trúc trong mỗi app: src/domain, src/application, src/infrastructure, src/presentation.
 * packages/domain được coi là tầng domain.
 */
const DOMAIN = '^(packages/domain/src|apps/[^/]+/src/domain)/';

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
      from: { path: '^apps/[^/]+/src/application/' },
      to: {
        path: [
          '^apps/[^/]+/src/(infrastructure|presentation)/',
          '(^|/)node_modules/(react|react-native|react-dom)/',
        ],
      },
    },
    {
      name: 'presentation-no-infrastructure',
      comment: 'Presentation không import infrastructure; phụ thuộc được tiêm qua composition root.',
      severity: 'error',
      from: { path: '^apps/[^/]+/src/presentation/' },
      to: { path: '^apps/[^/]+/src/infrastructure/' },
    },
  ],
  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: '(^|/)(dist|coverage|\\.expo)/' },
    tsPreCompilationDeps: true,
    combinedDependencies: true,
  },
};
