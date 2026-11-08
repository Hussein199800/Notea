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

## Features & constraints added in v1.1
- Notes are ordered **blocks** (`note_blocks`, schema v3): text / checklist / attachment. `notes.body` = text blocks joined by a blank line (canonical for search, sync, previews). Locked notes keep the layout only inside the encrypted payload. A note without block rows uses the classic layout.
- Images: `ImageNormalizer` (HEIC/AVIF -> JPEG, TIFF via `TiffDecoder` -> PNG/JPEG, unsupported kept as file). OCR is on-device Tesseract (ara+eng) in `OcrWorker`; never stores OCR text for locked notes.
- Versions: named versions (label) are never trimmed; diff via `TextDiff`.
- Phone-to-phone transfer: raw TCP on the LAN, payload is a password-encrypted backup, the one-time code is the password.
- AI assistant: user's own Gemini key only (encrypted, never synced/backed up), explicit consent before sending, input capped at 12k chars. Per request: one note, one selection, one image or one recording (≤ 14 MB). The only multi-note feature is «اسأل ملاحظاتك» (owner-approved v1.4): retrieval runs on the device (`NoteMemory.Index.search`) and at most 5 unlocked notes are sent, listed by name in the consent dialog. Locked notes and encrypted attachments are never sent. Changes made by the assistant always keep a version first. Notea Memory is on-device (no AI).

## Owner's work standard (v1.2 — apply to all future work)
- Replies to the owner: Arabic only, short, direct, practical. Questions via the options tool in Arabic, recommended option first.
- Never push to `main` without the owner's approval and a new version number. Work on the designated branch.
- Understand the project before changing it; never remove a feature or change behaviour without reason + approval. Implement, don't just suggest; prefer the simplest solution.
- Navigation: 4 tabs (الملاحظات، المهام والتذكيرات، المجلدات والوسوم، المزيد) — `ui/screens/Tabs.kt`. Bottom bar on phones, rail when wide; two balanced columns when width ≥ 700dp (`TabPage`).
- Sizes: verify 320dp, 390dp, phone landscape, tablet portrait (phone layout scaled to a 560dp canvas, `ScaledLayout`/`tabletScale`), tablet landscape. Touch targets ≥ 44dp. Text size setting multiplies fontScale.
- Colours: one fixed colour per concept (`ui/theme/Concepts.kt`); vivid g1/g2 only under icons, text colours keep ≥ 4.5:1 (tested).
- Arabic: Latin digits (`ar-u-nu-latn`), numbers isolated with U+2068/U+2069 (`Strings.num`), Latin versions/dates/paths inside Arabic wrapped in U+2066…U+2069; Arabic count forms via `countAr`/`notesCount`; numeric inputs accept ٠-٩ ۰-۹ «٫» (`core/text/Digits`).
- Interaction: every number opens its list and has ⓘ (`InfoButton`) when its meaning isn't obvious; «محفوظ ✓» after a setting changes (`SavedTick`); errors = cause + fix; delete → Undo snackbar (`messageWithUndo`), permanent delete → confirm; long press = multi-select; back clears search/filter, then returns to the notes tab.
- Settings: real value, one-line explanation with the default, persisted, applied immediately, «استعادة الافتراضي».
- Help: tour (6 steps, re-run from «المزيد»), «ما الجديد» once per version, releases page. Diagnostics log = last 50 events, masked (`core/Diagnostics.mask`).
- Version lives in 3 places — `app/build.gradle.kts` versionName, `core/Changelog.kt` (newest first), `release-notes.md` (first line `# Notea <version>`) — enforced by `UpdateAndVersionTest`. versionCode = (major×10000+minor×100+patch)×10 + abi (universal 0, armeabi-v7a 1, arm64-v8a 2).
- Updates: GitHub releases of Hussein199800/Notea, checked every 12h + manually; per-ABI APK, universal `Notea.apk` = fixed link `releases/latest/download/Notea.apk`; installer opened only after package/newer/signature checks (`core/update/UpdateRules`).
- CI (`.github/workflows/android.yml`): tests on every push; signed per-ABI release only with the NOTEA_* secrets; publishes on `main` once per version; debug builds are never published; the key is never uploaded.
- Background: heavy work goes through WorkManager; OCR runs as a single serial queue with a foreground progress notification.
- Before any push: all unit tests + `assembleRelease`, screenshots reviewed by eye. Never claim a test that wasn't run.

## v1.3 additions
- Sub-notes: `notes.parentId` (schema v4, additive `MIGRATION_3_4`); `NoteDraft.parentId/icon = null` means "keep stored value" — change them only via `NoteRepository.setParent` (refuses cycles) / `setIcon` / `restoreMeta` (backups). The main list hides sub-notes whose parent is live; search/folders/tags show all.
- Block types: `text`, `toggle` (first line = heading), `callout`, `checklist`, `attachment`. `BlockType.TEXTUAL` blocks are joined into `notes.body`.
- Editor: «/» at a line start opens the insert menu; «[[» opens the note picker; `[[Title]]` links resolve by Arabic-normalized title (`core/text/NoteLinks`); backlinks never read locked notes.
- OCR: on-device Tesseract stays automatic; «قراءة دقيقة» sends ONE image (downscaled ≤ 2000px) to Gemini with the user's key after consent, and replaces the stored OCR text. Never for locked/encrypted attachments.
- Scanning: if Google's scanner isn't ready, show the chooser (install module with progress / phone camera document mode then gallery / quick photo / gallery) — never a dead end.
- Samsung Notes `.sdocx` import is best-effort (UTF-16 text runs + media images) and always flags FORMATTING_LOST.
- Colours: every tab and every action icon uses its Concept colour (trash = TRASH.g2, delete forever = TRASH.t2, archive = ARCHIVE, pin = SECURITY, favourite = TASKS, folder/sub-notes = FOLDERS, links = REMINDERS).
