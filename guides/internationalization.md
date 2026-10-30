# Management frontend

The login screen and application header offer English and Simplified Chinese.
English is the default. The selected language is saved under `xipki.locale` in
browser storage; switching also works for the current session when storage is disabled.

Internationalization uses **i18next + react-i18next**. Existing pages use
`useI18n().t(...)`; API notifications use the same engine. English source messages
remain stable keys, and dynamic messages use named placeholders such as `{name}`.
Ant Design controls and the date picker follow the selected locale. Technical
identifiers, entered values and unknown server diagnostics retain their original
content. Missing translations fall back to English.

Translations are bundled with the application; no translation server, external
service or runtime network access is required. Language changes preserve page
forms and drafts.

## Translation files

The paths below are relative to `console-ui/`. Each language has JSON files under `src/i18n/locales/<language>/`:

| Namespace | Content |
| --- | --- |
| `common` | Shared controls, navigation, login and notifications |
| `access` | Users, roles, grants and permission labels |
| `ca` | CA configuration, lifecycle and associations |
| `certificates` | Enrollment, certificates and CRLs |
| `resources` | Profiles, signers, requestors, publishers and key generators |
| `system` | System management, database parameters and audit |
| `guide` | Dashboard onboarding guide |

For example, `t('Delete {name}?', {name: 'root-ca'})` looks up a flat JSON key.
The English resource contains:

```json
{
  "Delete {name}?": "Delete {name}?"
}
```

Other languages keep the same key and translate only the value.
A key belongs to only one namespace;
put text shared across modules in `common`. Pages resolve the namespaces through
the shared adapter, so moving a key between modules requires no page changes.
Periods, colons and technical identifiers in keys are treated literally.

For counts, pass a **number** as `count`. Add the plural forms required by the
target language (`Intl.PluralRules(language).resolvedOptions().pluralCategories`).
For example, English defines `{count} permissions_one` and
`{count} permissions_other`, while Chinese only needs
`{count} permissions_other`. Keep the unsuffixed entry as the no-count fallback.
Values retain the existing single-brace placeholder format.

## Add a language

1. Copy `src/i18n/locales/en-US/` to a new language directory, such as `ja-JP`,
   and translate the JSON **values**, preserving keys and named placeholders.
   Adjust plural suffixes to that language's rules.
2. In `src/i18n/languages.ts`, import those seven JSON modules, the corresponding
   Ant Design locale and the Day.js locale registration. Add one entry to
   `languages` containing the native language label, `antd`, `dayjs` and `messages`,
   following the existing entries. Use `componentLocale(...)` for the Ant Design
   import so the browser build and Node language checks use the same locale.
3. Run `npm test` and `npm run build`, then verify the new language in the browser,
   including date pickers, validation messages and longer text layouts.

The locale type, switcher options, supported languages and translation resources
are derived from this registry. Business pages and backend APIs do not need edits.
An Ant Design locale must be available, or an explicit component-language fallback
must be chosen for a language that Ant Design does not provide.

Tests check every registered language for complete base keys, namespace and key
consistency, required plural forms, nonempty values and matching placeholders.
Fallbacks protect runtime behavior; incomplete language packs fail the checks
before a production image is built.

## Verification

From `console-ui/`, run `npm test` and `npm run build` with Node 22.16 or newer
before packaging the backend or Docker image. In the browser, verify language
selection on the login page and application header, persistence after refresh,
form values during switching, and localized validation and date controls.
