# Notea — project rules

## Build and verify

- Use the Gradle wrapper with JDK 17 or later and Android SDK Platform 36.1. Android Studio can install the required Build Tools. Set `ANDROID_HOME` or configure `sdk.dir` in the ignored `local.properties`.
- Build and test the on-device configuration with `./gradlew :app:assembleDebug :app:testDebugUnitTest`. The debug APK is written to `app/build/outputs/apk/debug/`. A Firebase configuration and a release signing key are optional for debug work.
- The Robolectric suite covers Room migrations, backup and import, editor formatting, and media logic. Run the affected test class with `--tests '*ClassName'` when iterating. Use `./gradlew :app:connectedDebugAndroidTest` with a device or emulator to verify Android intent and document-provider behavior.
- After a UI change, render screenshots in Arabic and English and inspect mixed-direction content. Set an absolute output directory with `NOTEA_SHOTS=/path/to/screenshots ./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest' --tests '*NewScreensShotTest'`.

## Code map

- `ui/NoteaRoot.kt` provides navigation and the app's layout direction. Compose screens live in `ui/screens`, reusable UI in `ui/components`, and the Arabic and English labels in `ui/i18n/Strings.kt`.
- `ui/viewmodel/EditorViewModel.kt` coordinates editor changes. `ui/editor/MarkdownEngine.kt` handles Markdown. `data/repository/Models.kt` defines the ordered block format, and `NoteRepository` persists it.
- `data/importer` parses provider exports and shared files. `data/backup` reads and writes the Notea backup format, including attachments and block references. Room schemas and migrations live in `app/schemas` and `data/local`.
- `data/repository/AttachmentStore.kt` stores attachment bytes. `ui/platform/Media.kt` integrates with Android viewers, and `ocr` handles on-device extraction and indexing.

## Text layout & formatting (owner's top priority — apply to every screen, dialog, widget and future feature)
- **Text always wraps.** Never truncate user-visible text to a single line with "…" (`maxLines = 1` / `TextOverflow.Ellipsis` are not allowed for content, titles, labels, chips, list rows, dialogs or snackbars).
- **Long text gets "عرض المزيد / عرض أقل" (Show more / Show less).** Use `ExpandableText` (`ui/components/Components.kt`): it collapses to a sensible number of lines and shows the toggle only when the text actually overflows. Long lists (e.g. checklist previews) get "عرض كل العناصر".
- Containers must grow with their text: use `heightIn(min = …)` instead of fixed `height(…)` for rows, bars and chips.
- Input fields: only secrets (PIN, passwords) are single-line. Other inputs wrap (`maxLines = 3–4`), stripping newlines where a newline is meaningless.
- Home-screen widgets cannot expand: allow 2–3 wrapped lines; tapping opens the full note.
- Formatting quality matters a lot: Arabic-first RTL, correct bidi for mixed Arabic/Latin (avoid Latin tokens starting/ending with punctuation inside Arabic — write "بصيغة notea" not ".notea"; put spaces around Latin words next to "و"; check the rendered screenshot), consistent spacing, Rounded icon set, emerald/ivory/gold/charcoal palette, IBM Plex Sans Arabic.
- Keep UI layout direction tied to the selected language and derive paragraph direction from the actual note content. Exercise Arabic paragraphs inside an English UI, mixed Arabic and Latin text, and LTR code and identifiers.

## Data safety
- Room is the source of truth; migrations must be non-destructive (never `fallbackToDestructiveMigration`) and covered by `MigrationTest`.
- Never store or upload plaintext of locked notes; encryption failures abort the write.
- Keep OCR text out of attachments on locked notes. Persist OCR results through `NoteRepository.setExtractedText` so the lock check, update, and search indexing occur in the same transaction.
- Use the Storage Access Framework for picking and exporting files. Store attachment bytes in app-private storage, and use narrow FileProvider paths for viewers that need a URI. Resolve links in imported archives against the selected inputs.
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
- AI assistant: user's own Gemini key only (encrypted, never synced/backed up), explicit consent before sending, one note per request, input capped at 12k chars; no whole-library AI features (token cost). Notea Memory is on-device (no AI).
