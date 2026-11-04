# Notea — project rules

## Text layout & formatting (owner's top priority — apply to every screen, dialog, widget and future feature)
- **Text always wraps.** Never truncate user-visible text to a single line with "…" (`maxLines = 1` / `TextOverflow.Ellipsis` are not allowed for content, titles, labels, chips, list rows, dialogs or snackbars).
- **Long text gets "عرض المزيد / عرض أقل" (Show more / Show less).** Use `ExpandableText` (`ui/components/Components.kt`): it collapses to a sensible number of lines and shows the toggle only when the text actually overflows. Long lists (e.g. checklist previews) get "عرض كل العناصر".
- Containers must grow with their text: use `heightIn(min = …)` instead of fixed `height(…)` for rows, bars and chips.
- Input fields: only secrets (PIN, passwords) are single-line. Other inputs wrap (`maxLines = 3–4`), stripping newlines where a newline is meaningless.
- Home-screen widgets cannot expand: allow 2–3 wrapped lines; tapping opens the full note.
- Formatting quality matters a lot: Arabic-first RTL, correct bidi for mixed Arabic/Latin (avoid Latin tokens starting/ending with punctuation inside Arabic — write "بصيغة notea" not ".notea"; put spaces around Latin words next to "و"; check the rendered screenshot), consistent spacing, Rounded icon set, emerald/ivory/gold/charcoal palette, IBM Plex Sans Arabic.
- Verify visually after UI changes: `NOTEA_SHOTS=<dir> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'` renders AR/EN, light/dark.

## Data safety
- Room is the source of truth; migrations must be non-destructive (never `fallbackToDestructiveMigration`) and covered by `MigrationTest`.
- Never store or upload plaintext of locked notes; encryption failures abort the write.
- Any Firestore field change updates `firestore.rules`, `firestore.test.js` and `security_spec.md` together.

## Identity & build
- Independent app: `applicationId = com.notea.app` (debug builds use `.debug` suffix). No AI Studio tooling.
- Developer identity shown in About / Settings signature: حسين الطويل — Hussein Taweel (GitHub Hussein199800). Keep the Seal-style About page.
- Release signing comes only from `keystore.properties` (gitignored) or `NOTEA_KEYSTORE_PATH` / `NOTEA_KEYSTORE_PASSWORD` / `NOTEA_KEY_ALIAS` / `NOTEA_KEY_PASSWORD`. Never commit `*.jks` or passwords.
- Firebase is optional: without `app/google-services.json` the app runs in on-device mode (`LOCAL_UID`). With it, sign-in + sync appear and `AccountLinker` moves on-device notes into the account.
