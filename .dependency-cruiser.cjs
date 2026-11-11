/**
 * Module boundaries of the frontend workspace (docs/design/frontend-architecture.md).
 * Run: pnpm check:boundaries   (also a step of check_all.ps1)
 *
 * Layers, from the bottom:  token <- theme,  api-client,  domain (labels, form mappers),  ui (primitives)
 *                           apps/admin: services -> features -> app (routes)
 */

const ADMIN = '^apps/admin/src';

/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      comment: 'A cycle makes it impossible to say which module sits below the other.',
      severity: 'error',
      from: {},
      to: { circular: true },
    },

    {
      name: 'not-to-unresolvable',
      comment:
        'An import that cannot be resolved is usually an undeclared workspace dependency; it would also hide the other boundary rules.',
      severity: 'error',
      from: {},
      to: { couldNotResolve: true },
    },

    // ---- packages ------------------------------------------------------------------------------
    {
      name: 'ui-knows-nothing-of-the-domain',
      comment:
        '@itera/ui holds generic primitives. Domain words (booking, departure ...) belong to @itera/domain and to the features that combine both.',
      severity: 'error',
      from: { path: '^packages/ui/src' },
      to: { path: ['^packages/(domain|api-client)/', '^apps/'] },
    },
    {
      name: 'domain-knows-nothing-of-the-screen',
      comment: '@itera/domain is plain TypeScript: no components, no Next.js, no react-dom.',
      severity: 'error',
      from: { path: '^packages/domain/src' },
      to: { path: ['^packages/ui/', '^apps/'], pathNot: '^packages/domain/' },
    },
    {
      name: 'domain-uses-only-api-client-types',
      comment: 'The domain may name contract types, but must not call the client at runtime.',
      severity: 'error',
      from: { path: '^packages/domain/src' },
      to: { path: '^packages/api-client/', dependencyTypesNot: ['type-only'] },
    },
    {
      name: 'domain-and-ui-stay-off-next',
      comment: 'Next.js belongs to the apps; shared packages must stay usable from any host.',
      severity: 'error',
      from: { path: '^packages/(domain|ui)/src' },
      to: { path: '^node_modules/next/', dependencyTypes: ['npm', 'npm-dev', 'npm-peer', 'npm-no-pkg', 'npm-unknown'] },
    },
    {
      name: 'api-client-is-a-leaf',
      comment: '@itera/api-client is generated from the contract and depends on no other workspace package.',
      severity: 'error',
      from: { path: '^packages/api-client/src' },
      to: { path: '^packages/', pathNot: '^packages/api-client/' },
    },
    {
      name: 'token-and-theme-are-leaves',
      comment: 'Tokens and themes are values; they import no code.',
      severity: 'error',
      from: { path: '^packages/(token|theme)/src' },
      to: { path: ['^packages/(domain|ui|api-client)/', '^apps/'] },
    },
    {
      name: 'packages-do-not-reach-into-apps',
      severity: 'error',
      from: { path: '^packages/' },
      to: { path: '^apps/' },
    },

    // ---- apps/admin ----------------------------------------------------------------------------
    {
      name: 'only-services-call-the-api-client',
      comment:
        'Pages and features talk to the backend through services/ (token handling, base URL). Contract types may be imported anywhere.',
      severity: 'error',
      from: { path: `${ADMIN}/(app|features)` },
      to: { path: '^packages/api-client/', dependencyTypesNot: ['type-only'] },
    },
    {
      name: 'services-are-below-features',
      severity: 'error',
      from: { path: `${ADMIN}/services` },
      to: { path: [`${ADMIN}/features`, `${ADMIN}/app`] },
    },
    {
      name: 'features-are-below-routes',
      comment: 'Routes (app/) compose features, never the other way round.',
      severity: 'error',
      from: { path: `${ADMIN}/features` },
      to: { path: `${ADMIN}/app` },
    },
    {
      name: 'features-do-not-import-each-other',
      comment: 'A feature may use only features/shell (the shared frame and form helpers), not a sibling feature.',
      severity: 'error',
      from: { path: `${ADMIN}/features/([^/]+)/` },
      to: { path: `${ADMIN}/features/[^/]+/`, pathNot: [`${ADMIN}/features/$1/`, `${ADMIN}/features/shell/`] },
    },
    {
      name: 'shell-does-not-know-features',
      comment: 'features/shell is shared by every feature, so it must not depend on one of them.',
      severity: 'error',
      from: { path: `${ADMIN}/features/shell/` },
      to: { path: `${ADMIN}/features/`, pathNot: `${ADMIN}/features/shell/` },
    },
  ],

  options: {
    doNotFollow: { path: 'node_modules' },
    exclude: { path: ['/generated/', '\\.next/', '/node_modules/'] },
    tsPreCompilationDeps: true,
    tsConfig: { fileName: 'apps/admin/tsconfig.json' },
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default', 'types'],
      mainFields: ['module', 'main', 'types'],
    },
    reporterOptions: { text: { highlightFocused: true } },
  },
};
