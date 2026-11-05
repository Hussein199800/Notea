package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.importer.ImportWarning
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class Strings(val isArabic: Boolean) {
    private fun t(ar: String, en: String) = if (isArabic) ar else en
    val locale: Locale = if (isArabic) Locale("ar") else Locale.ENGLISH

    // App / auth
    val appName = "Notea"
    val tagline = t("ملاحظاتك، منظّمة وآمنة وتعمل دون إنترنت", "Your notes — organized, private and offline-first")
    val signInGoogle = t("المتابعة عبر Google", "Continue with Google")
    val signingIn = t("جارٍ تسجيل الدخول…", "Signing in…")
    val signInFailed = t("تعذّر تسجيل الدخول", "Sign-in failed")
    val signInNote = t("تُحفظ ملاحظاتك على جهازك أولاً ثم تُزامَن مع حسابك.", "Notes are saved on your device first, then synced to your account.")
    val signOut = t("تسجيل الخروج", "Sign out")
    val useWithoutAccount = t("استخدام بدون حساب", "Use without an account")
    val withoutAccountNote = t("تبقى ملاحظاتك على هذا الجهاز فقط. يمكنك تسجيل الدخول لاحقاً للمزامنة دون أن تفقد شيئاً.", "Your notes stay on this device only. You can sign in later to sync without losing anything.")
    val localAccount = t("على هذا الجهاز فقط (بدون حساب)", "On this device only (no account)")
    val aiCredit = t("نماذج التعرّف على النص: Tesseract (Apache 2.0)", "OCR models: Tesseract (Apache 2.0)")
    val signInToSync = t("تسجيل الدخول للمزامنة", "Sign in to sync")
    val syncNotConfigured = t("المزامنة السحابية غير مفعّلة في هذا الإصدار. احمِ ملاحظاتك بالنسخ الاحتياطي، والنسخة التلقائية اليومية تعمل.", "Cloud sync isn't enabled in this build. Protect your notes with backups; the daily automatic backup is on.")
    val localSyncInfo = t("ملاحظاتك محفوظة على هذا الجهاز. سجّل الدخول لمزامنتها بين أجهزتك، وستنتقل كلها إلى حسابك.", "Your notes are saved on this device. Sign in to sync them across devices; they will all move to your account.")
    val linkingTitle = t("جارٍ نقل ملاحظاتك إلى حسابك…", "Moving your notes to your account…")
    val linkNeedsPassphrase = t("حسابك يملك خزنة تشفير مختلفة. أدخل عبارة مرورها لنقل ملاحظاتك المقفلة إليها.", "Your account already has a different encryption vault. Enter its passphrase to move your locked notes into it.")
    val linkNeedsInternet = t("يلزم اتصال بالإنترنت لنقل ملاحظاتك إلى حسابك. لن يُحذف شيء.", "An internet connection is needed to move your notes to your account. Nothing will be deleted.")
    val retry = t("إعادة المحاولة", "Retry")
    val later = t("لاحقاً", "Later")
    val linkDone = { n: Int -> t("نُقلت ${num(n)} ملاحظة إلى حسابك", "Moved $n notes to your account") }
    val signOutConfirm = t("ستبقى ملاحظاتك محفوظة على هذا الجهاز، وتعود عند تسجيل الدخول بالحساب نفسه.", "Your notes stay on this device and come back when you sign in with the same account.")
    val language = t("اللغة", "Language")
    val switchLanguage = t("English", "العربية")

    // Navigation
    val allNotes = t("كل الملاحظات", "All notes")
    val favorites = t("المفضلة", "Favorites")
    val reminders = t("التذكيرات", "Reminders")
    val archive = t("الأرشيف", "Archive")
    val trash = t("المحذوفات", "Trash")
    val folders = t("المجلدات", "Folders")
    val tags = t("الوسوم", "Tags")
    val settings = t("الإعدادات", "Settings")
    val templates = t("القوالب", "Templates")
    val newFolder = t("مجلد جديد", "New folder")
    val folderName = t("اسم المجلد", "Folder name")
    val rename = t("إعادة تسمية", "Rename")
    val delete = t("حذف", "Delete")
    val deleteFolderInfo = t("ستنتقل ملاحظات هذا المجلد إلى «كل الملاحظات» ولن تُحذف.", "Notes in this folder move to “All notes”; nothing is deleted.")
    val noFolder = t("بدون مجلد", "No folder")
    val menu = t("القائمة", "Menu")

    // Home
    val searchHint = t("ابحث في الملاحظات والوسوم والمرفقات", "Search notes, tags and attachments")
    val newNote = t("ملاحظة جديدة", "New note")
    val fromTemplate = t("من قالب", "From template")
    val pinned = t("المثبّتة", "Pinned")
    val others = t("الأخرى", "Others")
    val emptyAllTitle = t("ابدأ أول ملاحظة", "Start your first note")
    val emptyAllBody = t("اكتب فكرة، أنشئ قائمة مهام، أو استورد ملاحظاتك من تطبيق آخر.", "Write an idea, make a checklist, or import notes from another app.")
    val emptyFiltered = t("لا توجد ملاحظات هنا", "Nothing here yet")
    val emptySearch = t("لا نتائج مطابقة", "No matching notes")
    val trashBanner = t("تُحذف الملاحظات نهائياً بعد ٣٠ يوماً في المحذوفات.", "Notes are deleted forever after 30 days in Trash.")
    val emptyTrash = t("إفراغ المحذوفات", "Empty trash")
    val emptyTrashConfirm = t("حذف كل ما في المحذوفات نهائياً؟ لا يمكن التراجع.", "Permanently delete everything in Trash? This cannot be undone.")
    val gridView = t("عرض شبكي", "Grid view")
    val listView = t("عرض قائمة", "List view")
    val sort = t("الترتيب", "Sort")
    val sortModified = t("آخر تعديل", "Last edited")
    val sortCreated = t("تاريخ الإنشاء", "Date created")
    val sortTitle = t("العنوان", "Title")
    val sortColor = t("اللون", "Color")
    val filters = t("الفلاتر", "Filters")
    val clearFilters = t("مسح الفلاتر", "Clear filters")
    val typeFilter = t("النوع", "Type")
    val dateFilter = t("التاريخ", "Date")
    val colorFilter = t("اللون", "Color")
    val typeChecklist = t("مهام", "Checklists")
    val typeImages = t("صور", "Images")
    val typeAudio = t("صوت", "Audio")
    val typeFiles = t("ملفات و PDF", "Files & PDF")
    val typeLinks = t("روابط", "Links")
    val typeReminder = t("بتذكير", "With reminder")
    val typeLocked = t("مقفلة", "Locked")
    val dateAny = t("أي وقت", "Any time")
    val dateToday = t("اليوم", "Today")
    val dateWeek = t("آخر ٧ أيام", "Last 7 days")
    val dateMonth = t("آخر ٣٠ يوماً", "Last 30 days")
    val dateYear = t("هذا العام", "This year")
    val results = { n: Int -> t("${num(n)} نتيجة", "$n results") }
    val lockedPreview = t("ملاحظة مقفلة", "Locked note")
    val untitled = t("بلا عنوان", "Untitled")
    val selectedCount = { n: Int -> t("${num(n)} محددة", "$n selected") }

    // Note actions
    val pin = t("تثبيت", "Pin")
    val unpin = t("إلغاء التثبيت", "Unpin")
    val favorite = t("إضافة للمفضلة", "Add to favorites")
    val unfavorite = t("إزالة من المفضلة", "Remove from favorites")
    val archiveAction = t("أرشفة", "Archive")
    val unarchive = t("إلغاء الأرشفة", "Unarchive")
    val moveToTrash = t("نقل إلى المحذوفات", "Move to trash")
    val restore = t("استعادة", "Restore")
    val deleteForever = t("حذف نهائي", "Delete forever")
    val deleteForeverConfirm = t("ستُحذف هذه الملاحظة ومرفقاتها نهائياً.", "This note and its attachments will be deleted permanently.")
    val color = t("اللون", "Color")
    val moveToFolder = t("نقل إلى مجلد", "Move to folder")
    val share = t("مشاركة كنص", "Share as text")
    val movedToTrash = t("نُقلت إلى المحذوفات", "Moved to trash")
    val undo = t("تراجع", "Undo")
    val redo = t("إعادة", "Redo")
    val archived = t("تمت الأرشفة", "Archived")

    // Editor
    val titleHint = t("العنوان", "Title")
    val bodyHint = t("اكتب ملاحظتك…", "Start writing…")
    val checklist = t("قائمة مهام", "Checklist")
    val addItem = t("إضافة عنصر", "Add item")
    val itemHint = t("عنصر", "Item")
    val completedItems = { done: Int, all: Int -> t("${num(done)} من ${num(all)} منجزة", "$done of $all done") }
    val bold = t("عريض", "Bold")
    val italic = t("مائل", "Italic")
    val underline = t("تسطير", "Underline")
    val heading = t("عنوان", "Heading")
    val bullets = t("قائمة نقطية", "Bulleted list")
    val numbered = t("قائمة مرقمة", "Numbered list")
    val quote = t("اقتباس", "Quote")
    val link = t("رابط", "Link")
    val addLink = t("إضافة رابط", "Add link")
    val linkUrl = t("الرابط (https://…)", "URL (https://…)")
    val linkText = t("النص الظاهر (اختياري)", "Display text (optional)")
    val insertInText = t("إدراج في النص", "Insert in text")
    val addAsCard = t("إضافة كبطاقة مع معاينة", "Add as preview card")
    val linkPreviewNote = t("لجلب المعاينة سيتصل التطبيق بهذا الرابط فقط.", "To build the preview the app connects to this URL only.")
    val fetchingPreview = t("جارٍ جلب المعاينة…", "Fetching preview…")
    val image = t("صورة", "Image")
    val file = t("ملف", "File")
    val record = t("تسجيل صوتي", "Voice note")
    val stopRecording = t("إيقاف التسجيل", "Stop recording")
    val recording = t("جارٍ التسجيل", "Recording")
    val micPermission = t("يلزم إذن الميكروفون لتسجيل الملاحظات الصوتية.", "Microphone permission is needed to record voice notes.")
    val attachments = t("المرفقات", "Attachments")
    val scanIncompleteTitle = t("لم يكتمل المسح", "Scan didn't finish")
    val scanIncompleteBody = t("إذا ظهرت لك رسالة «حدث خطأ ما» من خدمات Google، فماسح Google غير متاح حالياً على هذا الهاتف. يمكنك التقاط صورة بالكاميرا بدلاً منه، وسيقرأ Notea النص منها على الجهاز.",
        "If Google services showed “Something went wrong”, Google's scanner isn't available on this phone right now. You can take a photo with the camera instead; Notea will read the text on the device.")
    val useCamera = t("التقاط بالكاميرا", "Use the camera")
    val scannerUsingCamera = t("ماسح Google غير جاهز على هذا الهاتف، فاستخدمنا الكاميرا مباشرة.", "Google's scanner isn't ready on this phone, so the camera is used instead.")
    val qrNotFound = t("لم نجد رمز QR في الصورة. قرّب الكاميرا من الرمز أو أدخل العنوان والرمز يدوياً.", "No QR code found in the photo. Move closer to the code, or enter the address and code manually.")
    val scanDocument = t("مسح مستند", "Scan document")
    val ocrTitle = t("النص المقروء من الصورة", "Text found in the image")
    val ocrPending = t("جارٍ قراءة النص على الجهاز…", "Reading text on the device…")
    val copy = t("نسخ", "Copy")
    val copied = t("نُسخ النص", "Text copied")
    val insertIntoNote = t("إدراج في الملاحظة", "Insert into note")
    val moveUp = t("نقل لأعلى", "Move up")
    val moveDown = t("نقل لأسفل", "Move down")
    val removeBlock = t("إزالة", "Remove")
    val removeAttachment = t("إزالة المرفق", "Remove attachment")
    val openFile = t("فتح", "Open")
    val noAppToOpen = t("لا يوجد تطبيق لفتح هذا الملف", "No app can open this file")
    val addTags = t("الوسوم", "Tags")
    val tagHint = t("أضف وسماً واضغط إدخال", "Add a tag and press enter")
    val reminder = t("تذكير", "Reminder")
    val setReminder = t("ضبط تذكير", "Set reminder")
    val removeReminder = t("إزالة التذكير", "Remove reminder")
    val pickDate = t("اختر التاريخ", "Pick date")
    val pickTime = t("اختر الوقت", "Pick time")
    val laterToday = t("لاحقاً اليوم (بعد ٣ ساعات)", "Later today (in 3 h)")
    val tomorrowMorning = t("غداً ٩:٠٠ صباحاً", "Tomorrow 9:00")
    val nextWeek = t("الأسبوع القادم", "Next week")
    val customTime = t("وقت مخصص…", "Custom…")
    val notificationPermission = t("فعّل الإشعارات ليصلك التذكير.", "Allow notifications to receive reminders.")
    val exactAlarmHint = t("قد يتأخر التذكير بضع دقائق. يمكنك السماح بالمنبهات الدقيقة من الإعدادات.", "Reminders may be a few minutes late. You can allow exact alarms in system settings.")
    val history = t("سجل التعديلات", "Version history")
    val noHistory = t("لا توجد نسخ سابقة بعد. تُحفظ نسخة تلقائياً كل بضع دقائق من التحرير.", "No earlier versions yet. A version is saved automatically every few minutes while editing.")
    val restoreVersion = t("استعادة هذه النسخة", "Restore this version")
    val versionRestored = t("استُعيدت النسخة، وحُفظت النسخة الحالية في السجل.", "Version restored; the current one was kept in history.")
    val reasonAuto = t("حفظ تلقائي", "Autosave")
    val reasonConflict = t("نسخة من جهاز آخر (تعارض)", "Other device (conflict)")
    val reasonBeforeRestore = t("قبل الاستعادة", "Before restore")
    val reasonMerge = t("قبل الدمج", "Before merge")
    val reasonNamed = t("نسخة محفوظة", "Saved version")
    val reasonAssistant = t("قبل تعديل المساعد", "Before assistant edit")
    val saveNamedVersion = t("حفظ نسخة باسم", "Save version as…")
    val versionName = t("اسم النسخة (مثلاً: قبل التعديل الكبير)", "Version name (e.g. before the big rewrite)")
    val compareWithCurrent = t("مقارنة مع الحالية", "Compare with current")
    val showVersionOnly = t("النسخة فقط", "This version only")
    val diffStats = { added: Int, removed: Int -> t("+${num(added)} −${num(removed)} كلمة", "+$added −$removed words") }
    val diffExplain = { added: Int, removed: Int -> t("منذ هذه النسخة: أُضيفت ${num(added)} كلمة (بالأخضر) وحُذفت ${num(removed)} (مشطوبة بالأحمر).",
        "Since this version: $added words added (green), $removed removed (red, struck through).") }
    val saveAsTemplate = t("حفظ كقالب", "Save as template")
    val templateName = t("اسم القالب", "Template name")
    val templateSaved = t("حُفظ القالب", "Template saved")
    val savedLocally = t("محفوظة على الجهاز", "Saved on device")
    val edited = { s: String -> t("عُدّلت $s", "Edited $s") }
    val created = { s: String -> t("أُنشئت $s", "Created $s") }
    val lockNote = t("قفل الملاحظة", "Lock note")
    val unlockNote = t("إلغاء القفل", "Remove lock")
    val readOnlyTrash = t("الملاحظة في المحذوفات. استعدها لتعديلها.", "This note is in Trash. Restore it to edit.")

    // Locks / vault
    val appLocked = t("Notea مقفل", "Notea is locked")
    val enterPin = t("أدخل رمز PIN", "Enter PIN")
    val wrongPin = t("رمز غير صحيح", "Incorrect PIN")
    val useDeviceLock = t("استخدام قفل الجهاز", "Use device lock")
    val setPin = t("تعيين رمز PIN", "Set PIN")
    val newPin = t("رمز PIN جديد (٤–٨ أرقام)", "New PIN (4–8 digits)")
    val confirmPin = t("تأكيد الرمز", "Confirm PIN")
    val pinMismatch = t("الرمزان غير متطابقين", "PINs don't match")
    val pinTooShort = t("الرمز قصير جداً", "PIN is too short")
    val pinHint = t("تلميح (اختياري)", "Hint (optional)")
    val unlockNoteTitle = t("فتح الملاحظة المقفلة", "Unlock note")
    val hintLabel = { h: String -> t("التلميح: $h", "Hint: $h") }
    val vaultTitle = t("خزنة التشفير", "Encryption vault")
    val vaultSetupTitle = t("إنشاء عبارة مرور الخزنة", "Create a vault passphrase")
    val vaultSetupBody = t(
        "تُشفَّر الملاحظات المقفلة ومرفقاتها بمفتاح رئيسي محميّ بهذه العبارة. ستحتاجها لفتح ملاحظاتك المقفلة على جهاز آخر أو بعد الاستعادة. لا يمكن لأحد — ولا نحن — استرجاعها إن نسيتها.",
        "Locked notes and their attachments are encrypted with a master key protected by this passphrase. You'll need it to open locked notes on another device or after a restore. Nobody — including us — can recover it if you forget it."
    )
    val passphrase = t("عبارة المرور (٨ أحرف على الأقل)", "Passphrase (at least 8 characters)")
    val confirmPassphrase = t("تأكيد عبارة المرور", "Confirm passphrase")
    val passphraseMismatch = t("العبارتان غير متطابقتين", "Passphrases don't match")
    val passphraseShort = t("العبارة قصيرة جداً", "Passphrase is too short")
    val vaultUnlockTitle = t("فتح الخزنة على هذا الجهاز", "Unlock vault on this device")
    val vaultUnlockBody = t("أدخل عبارة مرور الخزنة التي أنشأتها سابقاً لفتح ملاحظاتك المقفلة.", "Enter the vault passphrase you created earlier to open your locked notes.")
    val wrongPassphrase = t("عبارة المرور غير صحيحة", "Wrong passphrase")
    val vaultReady = t("الخزنة جاهزة على هذا الجهاز", "Vault is ready on this device")
    val vaultNeedsUnlock = t("أدخل عبارة المرور لفتح الملاحظات المقفلة هنا", "Enter your passphrase to open locked notes here")
    val vaultNotSet = t("لم تُنشأ بعد — تُنشأ عند قفل أول ملاحظة", "Not set up — created when you lock your first note")
    val changePassphrase = t("تغيير عبارة المرور", "Change passphrase")
    val working = t("جارٍ العمل…", "Working…")
    val protectOldNotes = { n: Int -> t("${num(n)} ملاحظة مقفلة من الإصدار السابق تحتاج إلى إعادة تشفير", "$n locked notes from the previous version need re-encryption") }
    val protectNow = t("تشفير الآن", "Encrypt now")
    val protectedResult = { up: Int, foreign: Int -> t("شُفّرت ${num(up)} ملاحظة. ${if (foreign > 0) "${num(foreign)} مشفّرة على جهاز آخر وتبقى كما هي." else ""}", "Encrypted $up notes.${if (foreign > 0) " $foreign were encrypted on another device and were kept as is." else ""}") }
    val foreignLegacy = t("شُفّرت هذه الملاحظة بالإصدار القديم على جهاز آخر، ولا يمكن فتحها إلا هناك. افتحها على ذلك الجهاز بعد التحديث ليُعاد تشفيرها بالخزنة.", "This note was encrypted by the old version on another device and can only be opened there. Open it on that device after updating to re-encrypt it with the vault.")
    val decryptFailed = t("تعذّر فك التشفير. لم يتغيّر أي شيء.", "Could not decrypt. Nothing was changed.")
    val encryptionFailedNotSaved = t("تعذّر التشفير، لذلك لم يُحفظ النص. حاول مرة أخرى.", "Encryption failed, so the text was not saved. Please try again.")

    // Settings
    val account = t("الحساب", "Account")
    val appearance = t("المظهر", "Appearance")
    val theme = t("السمة", "Theme")
    val themeSystem = t("حسب النظام", "System")
    val themeLight = t("فاتح", "Light")
    val themeDark = t("داكن", "Dark")
    val langArabic = "العربية"
    val langEnglish = "English"
    val langSystem = t("لغة الجهاز", "Device language")
    val security = t("الأمان والخصوصية", "Security & privacy")
    val appLock = t("قفل التطبيق", "App lock")
    val appLockDesc = t("اطلب رمز PIN عند فتح التطبيق", "Ask for a PIN when opening the app")
    val changePin = t("تغيير رمز PIN", "Change PIN")
    val autoLock = t("القفل التلقائي", "Auto-lock")
    val immediately = t("فوراً", "Immediately")
    val minutes = { m: Int -> t("بعد ${num(m)} د", "After $m min") }
    val dataSection = t("البيانات", "Data")
    val importCenter = t("مركز الاستيراد", "Import center")
    val importCenterDesc = t("Samsung و Xiaomi و Huawei وملفات أخرى", "Samsung, Xiaomi, Huawei and other files")
    val backupRestore = t("النسخ الاحتياطي والاستعادة", "Backup & restore")
    val backupRestoreDesc = t("ملف كامل بصيغة notea مع المرفقات", "Complete .notea file with attachments")
    val sync = t("المزامنة", "Sync")
    val syncPending = { n: Int -> t("${num(n)} تغيير بانتظار الرفع", "$n changes waiting to upload") }
    val syncUpToDate = t("كل شيء متزامن", "Everything is synced")
    val syncNow = t("زامن الآن", "Sync now")
    val syncInfo = t("تُحفظ التعديلات على الجهاز فوراً وتُرفع عند توفر الإنترنت.", "Edits are saved on the device instantly and uploaded when online.")
    val attachmentsLocalNote = t("المرفقات تبقى على الجهاز وتنتقل بين الأجهزة عبر ملفات النسخ الاحتياطي بصيغة notea.", "Attachments stay on the device and move between devices via .notea backups.")
    val about = t("حول", "About")
    val version = t("الإصدار", "Version")
    val aboutNotea = t("حول Notea", "About Notea")
    // Developer identity — shown in About and as the signature line under Settings.
    val developerName = t("حسين الطويل", "Hussein Taweel")
    val designedBy = t("تصميم وتطوير", "Designed & developed by")
    val signature = t("صُنع بعناية ♥ بواسطة حسين الطويل", "Crafted with ♥ by Hussein Taweel")
    val aboutDescription = t(
        "تطبيق ملاحظات عربي الهوية: سريع، أنيق، يعمل دون إنترنت، ويحمي ملاحظاتك المقفلة بالتشفير. بياناتك ملكك — تصدّرها وتستعيدها متى شئت.",
        "An Arabic-first notes app: fast, elegant, offline-first, with real encryption for locked notes. Your data is yours — export and restore it any time."
    )
    val sourceCode = t("الشيفرة المصدرية", "Source code")
    val credits = t("شكر وتقدير", "Credits")
    val fontCredit = t("الخط", "Font")
    val libsCredit = t("المكتبات مفتوحة المصدر", "Open-source libraries")
    val githubAccount = t("حساب GitHub", "GitHub account")
    val debugBuild = t("نسخة تجريبية", "debug build")
    fun versionLabel(raw: String): String {
        val clean = raw.removeSuffix("-debug")
        return if (raw.endsWith("-debug")) "${version} $clean • $debugBuild" else "$version $clean"
    }
    val back = t("رجوع", "Back")
    val cancel = t("إلغاء", "Cancel")
    val ok = t("حسناً", "OK")
    val save = t("حفظ", "Save")
    val done = t("تم", "Done")
    val close = t("إغلاق", "Close")
    val next = t("التالي", "Next")
    val more = t("المزيد", "More")
    val showMore = t("عرض المزيد", "Show more")
    val showLess = t("عرض أقل", "Show less")
    val showAllItems = { n: Int -> t("عرض كل العناصر (${num(n)})", "Show all $n items") }
    val confirm = t("تأكيد", "Confirm")
    val enable = t("تفعيل", "Enable")

    // Import center
    val importIntro = t("اختر مصدر ملاحظاتك. ستشاهد معاينة كاملة قبل إضافة أي شيء.", "Choose where your notes come from. You'll see a full preview before anything is added.")
    val samsungNotes = "Samsung Notes"
    val xiaomiNotes = t("ملاحظات Xiaomi", "Xiaomi Notes")
    val huaweiNotepad = t("مفكرة Huawei", "Huawei Notepad")
    val otherFiles = t("ملفات أخرى و Google Keep", "Other files & Google Keep")
    val samsungSteps = t(
        "في Samsung Notes: حدّد الملاحظات ← مشاركة ← اختر «ملف Microsoft Word» أو «PDF» أو «ملف نصي» أو «صورة» ← احفظها أو شاركها مباشرة مع Notea. الكتابة اليدوية تُحفظ كملف PDF/صورة أصلية.",
        "In Samsung Notes: select notes → Share → choose Microsoft Word, PDF, Text or Image → save them or share directly to Notea. Handwriting is kept as the original PDF/image."
    )
    val xiaomiSteps = t(
        "في ملاحظات Xiaomi: افتح الملاحظة ← مشاركة ← «نص» ← Notea. يمكن مشاركة عدة ملاحظات أو ملفات TXT دفعة واحدة. إن لم يتوفر تصدير جماعي، استخدم «لصق نص» أدناه.",
        "In Xiaomi Notes: open a note → Share → Text → Notea. You can share several notes or TXT files at once. If bulk export isn't available, use “Paste text” below."
    )
    val huaweiSteps = t(
        "في مفكرة Huawei: حدد الملاحظات ← مشاركة/تصدير كـ TXT أو HTML أو صورة، أو صدّرها كملف ZIP. الملاحظات المكتوبة بخط اليد تُستورد كصور أو PDF مع حفظ التواريخ المتاحة.",
        "In Huawei Notepad: select notes → Share/Export as TXT, HTML or image, or export a ZIP. Handwritten notes are imported as images or PDF, keeping available dates."
    )
    val otherSteps = t(
        "يدعم: TXT و Markdown و HTML و DOCX و PDF و PNG/JPG و JSON و ZIP (ومنها تصدير Google Takeout لـ Keep).",
        "Supports TXT, Markdown, HTML, DOCX, PDF, PNG/JPG, JSON and ZIP (including Google Takeout exports of Keep)."
    )
    val chooseFiles = t("اختيار الملفات", "Choose files")
    val pasteText = t("لصق نص", "Paste text")
    val pasteTextTitle = t("استيراد من نص ملصق", "Import pasted text")
    val pasteTextHint = t("الصق ملاحظة أو أكثر. افصل بين الملاحظات بسطر يحتوي على ---", "Paste one or more notes. Separate notes with a line containing ---")
    val analyzing = t("جارٍ تحليل الملفات…", "Analyzing files…")
    val importing = t("جارٍ الاستيراد…", "Importing…")
    val preview = t("معاينة الاستيراد", "Import preview")
    val previewSummary = { notes: Int, att: Int, dup: Int -> t("${num(notes)} ملاحظة • ${num(att)} مرفق • ${num(dup)} مكررة", "$notes notes • $att attachments • $dup duplicates") }
    val importAction = t("استيراد", "Import")
    val importN = { n: Int -> t("استيراد ${num(n)}", "Import $n") }
    val skip = t("تجاهل", "Skip")
    val merge = t("دمج مع الموجودة", "Merge into existing")
    val keepBoth = t("الاحتفاظ بالنسختين", "Keep both")
    val exactDuplicate = t("مطابقة لملاحظة موجودة", "Same as an existing note")
    val similarDuplicate = t("عنوان مشابه لملاحظة موجودة", "Similar to an existing note")
    val importIntoFolder = t("وضعها في مجلد", "Put into a folder")
    val warnings = t("تنبيهات", "Warnings")
    val nothingToImport = t("لم نجد ملاحظات قابلة للاستيراد في هذه الملفات.", "No importable notes were found in these files.")
    val importDone = t("اكتمل الاستيراد", "Import complete")
    val importResult = { c: Int, m: Int, s: Int, a: Int -> t("أُضيفت ${num(c)} • دُمجت ${num(m)} • تُجوهلت ${num(s)} • مرفقات ${num(a)}", "Added $c • merged $m • skipped $s • attachments $a") }
    val failedItems = { n: Int -> t("تعذّر استيراد ${num(n)} عنصر", "$n items could not be imported") }
    val selectAll = t("تحديد الكل", "Select all")
    fun warning(w: ImportWarning) = when (w) {
        ImportWarning.FORMATTING_LOST -> t("بعض التنسيقات (جداول، رسوم، خطوط) لم تُنقل", "Some formatting (tables, drawings, fonts) was not converted")
        ImportWarning.TEXT_NOT_EXTRACTED -> t("لا يمكن استخراج النص؛ أُرفق الملف الأصلي", "Text can't be extracted; the original file is attached")
        ImportWarning.MISSING_RESOURCE -> t("صورة أو ملف مفقود من المصدر", "A referenced image or file is missing")
        ImportWarning.UNSUPPORTED -> t("نوع ملف غير مدعوم، تم تخطيه", "Unsupported file type, skipped")
        ImportWarning.UNREADABLE -> t("ملف تالف أو غير قابل للقراءة", "File is damaged or unreadable")
        ImportWarning.LOCKED_CONTENT -> t("ملاحظة مقفلة: تحتاج عبارة مرور الخزنة الأصلية لفتحها", "Locked note: needs the original vault passphrase to open")
        ImportWarning.TRUNCATED -> t("الأرشيف كبير جداً؛ تم تخطي جزء منه", "Archive too large; part of it was skipped")
        ImportWarning.IMAGE_KEPT_AS_FILE -> t("صيغة الصورة غير مدعومة على هذا الجهاز؛ حُفظ الملف الأصلي كمرفق", "This image format isn't supported on this device; the original file was kept")
    }

    // Backup
    val exportBackup = t("تصدير نسخة كاملة", "Export full backup")
    val exportBackupDesc = t("ملف بصيغة notea يضم الملاحظات والمجلدات والوسوم والمهام والتذكيرات والمرفقات والسجل.", "A .notea file with notes, folders, tags, checklists, reminders, attachments and history.")
    val encryptBackup = t("تشفير بكلمة مرور", "Encrypt with a password")
    val backupPassword = t("كلمة مرور النسخة (٨ أحرف على الأقل)", "Backup password (at least 8 characters)")
    val backupPasswordInfo = t("بدون كلمة المرور لا يمكن فتح النسخة. الملاحظات المقفلة تبقى مشفّرة بالخزنة في كل الأحوال.", "Without the password the backup can't be opened. Locked notes stay vault-encrypted either way.")
    val restoreBackup = t("استعادة من ملف", "Restore from file")
    val restoreBackupDesc = t("تُعرض المعاينة أولاً، ولا تُستبدل بياناتك الحالية.", "You'll see a preview first; your current data is never replaced.")
    val enterBackupPassword = t("هذه النسخة مشفّرة. أدخل كلمة المرور.", "This backup is encrypted. Enter its password.")
    val backupVaultPassphrase = t("تحتوي النسخة على ملاحظات مقفلة بخزنة أخرى. أدخل عبارة مرور تلك الخزنة لإعادة تشفيرها، أو تابع لإبقائها مقفلة كما هي.", "This backup has locked notes from another vault. Enter that vault's passphrase to re-encrypt them, or continue to keep them as they are.")
    val continueWithout = t("متابعة بدونها", "Continue without")
    val backupSaved = t("حُفظت النسخة الاحتياطية", "Backup saved")
    val backupFailed = t("فشل إنشاء النسخة", "Backup failed")
    val notABackup = t("هذا ليس ملف نسخة احتياطية من Notea", "This is not a Notea backup file")
    val corruptedBackup = t("الملف تالف (فشل التحقق من السلامة)", "The file is damaged (integrity check failed)")
    val newerBackup = t("أُنشئت هذه النسخة بإصدار أحدث من التطبيق. حدّث Notea أولاً.", "This backup was made by a newer app version. Update Notea first.")
    val backupContents = { m: Map<String, Int> -> t(
        "${num(m["notes"] ?: 0)} ملاحظة • ${num(m["folders"] ?: 0)} مجلد • ${num(m["attachments"] ?: 0)} مرفق • ${num(m["reminders"] ?: 0)} تذكير",
        "${m["notes"] ?: 0} notes • ${m["folders"] ?: 0} folders • ${m["attachments"] ?: 0} attachments • ${m["reminders"] ?: 0} reminders") }
    val localHistory = t("النسخ المحفوظة على الجهاز", "Versions on this device")
    val autoBackup = t("نسخة تلقائية يومية", "Daily automatic backup")
    val autoBackupDesc = t("يحتفظ بآخر ٧ نسخ داخل التطبيق", "Keeps the last 7 versions inside the app")
    val createSnapshotNow = t("إنشاء نسخة الآن", "Create a version now")
    val noSnapshots = t("لا توجد نسخ محلية بعد", "No local versions yet")
    val saveToFiles = t("حفظ في الملفات", "Save to files")
    val snapshotCreated = t("أُنشئت نسخة محلية", "Local version created")

    // Templates
    val noTemplates = t("لا توجد قوالب محفوظة بعد", "No saved templates yet")
    val builtIn = t("قوالب جاهزة", "Built-in")
    val yours = t("قوالبك", "Your templates")
    val tplMeeting = t("محضر اجتماع", "Meeting notes")
    val tplDaily = t("يومياتي", "Daily journal")
    val tplShopping = t("قائمة تسوق", "Shopping list")
    val tplProject = t("خطة مشروع", "Project plan")

    // Sharing
    // Migration center / onboarding
    val migrationCenter = t("مركز الترحيل", "Move your notes here")
    val migrationCenterDesc = t("انقل ملاحظاتك من هاتفك السابق أو تطبيق آخر", "Bring notes from your previous phone or another app")
    val welcomeTitle = t("أهلاً بك في Notea", "Welcome to Notea")
    val welcomeBody = t("هل لديك ملاحظات على هاتف سابق أو في تطبيق آخر؟ انقلها الآن؛ سترى معاينة وعدد الملاحظات والمرفقات قبل إضافة أي شيء.", "Have notes on a previous phone or in another app? Bring them now — you'll see a preview with note and attachment counts before anything is added.")
    val fromOtherPhone = t("من هاتف آخر عليه Notea", "From another phone with Notea")
    val fromOtherPhoneDesc = t("عبر نفس شبكة Wi-Fi، دون إنترنت ودون حساب", "Over the same Wi-Fi — no internet or account needed")
    val fromApps = t("من Samsung أو Xiaomi أو Huawei أو Google Keep", "From Samsung, Xiaomi, Huawei or Google Keep")
    val fromAppsDesc = t("ملفات TXT و DOCX و PDF والصور و HTML و ZIP", "TXT, DOCX, PDF, images, HTML and ZIP files")
    val fromBackupFile = t("من ملف نسخة احتياطية", "From a backup file")
    val fromBackupFileDesc = t("ملف بصيغة notea من هذا التطبيق", "A notea backup file from this app")
    val startFresh = t("ابدأ من جديد", "Start fresh")
    val transferTitle = t("النقل بين هاتفين", "Phone to phone")
    val transferIntro = t("صِل الهاتفين بنفس شبكة Wi-Fi، أو فعّل نقطة الاتصال في أحدهما واتصل بها من الآخر. لا حاجة للإنترنت، والنقل مشفّر بالرمز المؤقت.", "Connect both phones to the same Wi-Fi, or turn on a hotspot on one and join it from the other. No internet needed; the transfer is encrypted with the one-time code.")
    val sendFromThis = t("إرسال من هذا الهاتف (القديم)", "Send from this phone (old)")
    val receiveOnThis = t("استقبال على هذا الهاتف (الجديد)", "Receive on this phone (new)")
    val preparingTransfer = t("جارٍ تجهيز ملاحظاتك للنقل…", "Preparing your notes…")
    val scanOnNewPhone = t("على الهاتف الجديد: افتح «استقبال» وامسح هذا الرمز، أو اكتب العنوان والرمز.", "On the new phone: open “Receive” and scan this code, or type the address and code.")
    val transferAddress = t("العنوان", "Address")
    val transferCode = t("الرمز", "Code")
    val waitingForPhone = t("بانتظار الهاتف الجديد… أبقِ هذه الشاشة مفتوحة.", "Waiting for the new phone… keep this screen open.")
    val sending = t("جارٍ الإرسال", "Sending")
    val receiving = t("جارٍ الاستقبال", "Receiving")
    val transferDone = t("اكتمل الإرسال. يمكنك المتابعة على الهاتف الجديد.", "Sent. Continue on the new phone.")
    val transferFailed = t("تعذّر النقل. تأكد أن الهاتفين على الشبكة نفسها وأن الرمز صحيح.", "Transfer failed. Make sure both phones are on the same network and the code is correct.")
    val noWifi = t("لا يوجد اتصال Wi-Fi أو نقطة اتصال. صِل الهاتفين بالشبكة نفسها.", "No Wi-Fi or hotspot connection. Connect both phones to the same network.")
    val scanQr = t("مسح رمز QR", "Scan QR code")
    val enterManually = t("أو أدخل العنوان والرمز يدوياً", "Or enter the address and code")
    val connect = t("اتصال", "Connect")
    val invalidTransferCode = t("العنوان أو الرمز غير صحيح", "Invalid address or code")
    val scannerUnavailable = t("ماسح الرموز غير متاح على هذا الجهاز؛ أدخل الرمز يدوياً.", "QR scanner isn't available on this device; enter the code manually.")

    // Memory
    val fromMemory = t("من ذاكرتك", "From your memory")
    val memOnThisDay = { years: Int -> t("في مثل هذا اليوم قبل ${num(years)} ${if (years == 1) "سنة" else "سنوات"}", "On this day, $years year${if (years == 1) "" else "s"} ago") }
    val memDecision = t("قرار سجّلته سابقاً — هل ما زال سببه قائماً؟", "A decision you recorded — does the reason still hold?")
    val memOutdated = t("فيها مهام لم تُنجز وتواريخ مضت — قد تحتاج مراجعة", "Unfinished tasks with past dates — may need a review")
    val memForgotten = t("ملاحظة مهمة لم تفتحها منذ مدة", "An important note you haven't opened in a while")
    val open = t("فتح", "Open")
    val hideToday = t("إخفاء", "Hide")
    val relatedNotes = t("ملاحظات مرتبطة", "Related notes")
    val sharedWords = { w: String -> t("كلمات مشتركة: $w", "Shared words: $w") }
    val compareWithAssistant = t("مقارنة بالمساعد", "Compare with assistant")
    val tplDecision = t("قرار", "Decision")

    // Assistant
    val assistant = t("المساعد الذكي", "AI assistant")
    val assistantDesc = t("تلخيص، استخراج المهام، خطة عمل — بمفتاح Gemini الخاص بك", "Summary, tasks, action plan — with your own Gemini key")
    val assistantIntro = t(
        "يعمل المساعد بمفتاح Gemini الخاص بك: يتصل هاتفك بـ Google مباشرة، ولا يمر أي شيء عبرنا. الحدود والتكلفة حسب خطة حسابك (المجانية أو المدفوعة). لا يُرسل إلا الملاحظة التي تختارها وبعد موافقتك، ولا تُرسل الملاحظات المقفلة أبداً.",
        "The assistant uses your own Gemini key: your phone talks to Google directly, nothing goes through us. Limits and cost follow your account's plan (free or paid). Only the note you choose is sent, after you confirm; locked notes are never sent."
    )
    val enableAssistant = t("تفعيل المساعد", "Enable assistant")
    val keyStep1 = t("١. احصل على مفتاح مجاني من Google (تحتاج حساب Gmail فقط)", "1. Get a free key from Google (just a Gmail account)")
    val getFreeKey = t("فتح صفحة المفاتيح", "Open the key page")
    val keyStep2 = t("٢. اضغط «Create API key» ثم انسخ المفتاح", "2. Tap “Create API key” and copy it")
    val keyStep3 = t("٣. الصقه هنا واضغط «اختبار وحفظ»", "3. Paste it here and tap “Test & save”")
    val apiKey = t("مفتاح Gemini", "Gemini key")
    val paste = t("لصق", "Paste")
    val testAndSave = t("اختبار وحفظ", "Test & save")
    val keyWorks = t("✓ المفتاح يعمل", "✓ The key works")
    val keySaved = t("المفتاح محفوظ ومشفّر على هذا الجهاز فقط", "Key saved, encrypted, on this device only")
    val removeKey = t("حذف المفتاح", "Remove key")
    val modelInUse = { m: String -> t("النموذج: $m", "Model: $m") }
    val askBeforeSending = t("اسألني قبل كل إرسال", "Ask me before every send")
    val freeTierNote = t("تنبيه: في الخطة المجانية قد تستخدم Google ما يُرسل لتحسين خدماتها. لا ترسل معلومات حساسة.", "Note: on the free plan Google may use what you send to improve its services. Don't send sensitive information.")
    val errInvalidKey = t("المفتاح غير صحيح أو غير مفعّل", "The key is invalid or not enabled")
    val errRegion = t("خدمة Gemini غير متاحة في بلدك حالياً. بقية التطبيق يعمل كالمعتاد.", "Gemini isn't available in your country. The rest of the app works as usual.")
    val errQuota = t("انتهت الحصة المتاحة حالياً حسب خطة حسابك. حاول لاحقاً.", "Your plan's current quota is used up. Try again later.")
    val errOffline = t("لا يوجد اتصال بالإنترنت", "No internet connection")
    val errBlocked = t("لم يُرجع المساعد نتيجة لهذه الملاحظة", "The assistant returned no result for this note")
    val errGeneric = t("حدث خطأ غير متوقع", "Something went wrong")
    val actSummary = t("تلخيص", "Summarize")
    val actSummaryDesc = t("ملخص قصير وأهم النقاط", "A short summary and key points")
    val actTasks = t("استخراج المهام", "Extract tasks")
    val actTasksDesc = t("تحويل ما يجب فعله إلى قائمة مهام", "Turn to-dos into a checklist")
    val actPlan = t("خطة عمل", "Action plan")
    val actPlanDesc = t("خطوات مرتبة وواضحة من محتوى الملاحظة", "Clear ordered steps from the note")
    val consentTitle = t("إرسال إلى Google Gemini؟", "Send to Google Gemini?")
    val consentBody = { chars: Int, truncated: Boolean -> t(
        "سيُرسل عنوان هذه الملاحظة ونصها (${num(chars)} حرف${if (truncated) "، مقتطع لأول ١٢٬٠٠٠ حرف" else ""}) إلى Google لمعالجتها. لن تُرسل المرفقات ولا الملاحظات الأخرى.",
        "This note's title and text ($chars characters${if (truncated) ", truncated to the first 12,000" else ""}) will be sent to Google for processing. Attachments and other notes are not sent.") }
    val consentCompareBody = t("سيُرسل نص الملاحظتين المختارتين فقط إلى Google لمقارنتهما.", "Only the text of the two selected notes will be sent to Google to compare them.")
    val dontAskAgain = t("لا تسألني مجدداً على هذا الجهاز", "Don't ask again on this device")
    val send = t("إرسال", "Send")
    val thinking = t("جارٍ التحليل…", "Working…")
    val insertAtTop = t("إدراج في بداية الملاحظة", "Insert at the top")
    val addAsChecklist = t("إضافة كقائمة مهام", "Add as checklist")
    val insertPlan = t("إدراج الخطة في الملاحظة", "Insert plan into note")
    val summaryLabel = t("الملخص", "Summary")
    val planLabel = t("الخطة", "Plan")
    val goalLabel = t("الهدف", "Goal")
    val dueLabel = { d: String -> t("موعد: $d", "due: $d") }
    val noTasksFound = t("لم يجد المساعد مهاماً واضحة في هذه الملاحظة", "No clear tasks were found in this note")
    val contradictions = t("تعارضات محتملة", "Possible contradictions")
    val outdatedInfo = t("معلومات قد تكون قديمة", "Possibly outdated information")
    val nothingFound = t("لم يُعثر على شيء يُذكر", "Nothing notable found")
    val sharedNoteCreated = t("أُنشئت ملاحظة من المحتوى المشترك", "Created a note from shared content")

    // Widget
    val widgetName = t("ملاحظات Notea", "Notea notes")

    fun num(n: Int): String = if (isArabic) java.text.NumberFormat.getInstance(locale).format(n) else n.toString()

    fun date(millis: Long, withTime: Boolean = true): String {
        val f = if (withTime) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        else DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        return f.format(Date(millis))
    }

    fun relative(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        val min = diff / 60_000
        return when {
            min < 1 -> t("الآن", "just now")
            min < 60 -> t("قبل ${num(min.toInt())} د", "${min}m ago")
            min < 24 * 60 -> t("قبل ${num((min / 60).toInt())} س", "${min / 60}h ago")
            min < 7 * 24 * 60 -> t("قبل ${num((min / 1440).toInt())} يوم", "${min / 1440}d ago")
            else -> date(millis, withTime = false)
        }
    }
}

val LocalStrings = staticCompositionLocalOf { Strings(true) }
