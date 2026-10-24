package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.model.AutoLockTimeout
import com.example.data.model.LanguagePreference
import com.example.data.model.NoteCategory
import com.example.data.model.NoteSortOption
import com.example.data.model.SyncStatus
import com.example.data.model.ThemeMode

data class VaultStrings(
    val isArabic: Boolean,
    val appTitle: String,
    val appSubtitle: String,
    val welcomeHeadline: String,
    val welcomeDescription: String,
    val featureSyncTitle: String,
    val featureSyncDesc: String,
    val featureLockTitle: String,
    val featureLockDesc: String,
    val featureArabicTitle: String,
    val featureArabicDesc: String,
    val signInWithGoogle: String,
    val signingIn: String,
    val signOut: String,
    val searchPlaceholder: String,
    val newNote: String,
    val editNote: String,
    val noteTitlePlaceholder: String,
    val noteContentPlaceholder: String,
    val saveNote: String,
    val deleteNote: String,
    val moveToTrash: String,
    val restoreFromTrash: String,
    val deletePermanently: String,
    val emptyTrashButton: String,
    val trashAutoDeleteBanner: String,
    val archiveNote: String,
    val unarchiveNote: String,
    val pinNote: String,
    val unpinNote: String,
    val favoriteNote: String,
    val unfavoriteNote: String,
    val pinnedSection: String,
    val allNotesSection: String,
    val favoritesSection: String,
    val archivedSection: String,
    val trashSection: String,
    val emptyNotesTitle: String,
    val emptyNotesSubtitle: String,
    val emptyArchivedTitle: String,
    val emptyFavoritesTitle: String,
    val emptyTrashTitle: String,
    val lockedNoteLabel: String,
    val tapToUnlockNote: String,
    val lockNoteAction: String,
    val unlockNoteAction: String,
    val removeNoteLockAction: String,
    val appLockedTitle: String,
    val appLockedSubtitle: String,
    val unlockWithBiometricOrDevice: String,
    val enterPinPrompt: String,
    val enterNewPinPrompt: String,
    val confirmPinPrompt: String,
    val pinHintLabel: String,
    val pinHintPlaceholder: String,
    val invalidPinError: String,
    val pinTooShortError: String,
    val pinMismatchError: String,
    val securitySettingsTitle: String,
    val appLockToggleLabel: String,
    val appLockToggleDesc: String,
    val changeAppPinButton: String,
    val setAppPinTitle: String,
    val setNotePinTitle: String,
    val unlockNoteTitle: String,
    val lockVaultNow: String,
    val activeViewNotes: String,
    val activeViewFavorites: String,
    val activeViewArchive: String,
    val activeViewTrash: String,
    val colorLabel: String,
    val categoryLabel: String,
    val sortByLabel: String,
    val viewModeGrid: String,
    val viewModeList: String,
    val checklistTitle: String,
    val addChecklistItemPlaceholder: String,
    val addChecklistButton: String,
    val reminderLabel: String,
    val reminderPlaceholder: String,
    val attachPhotoButton: String,
    val photoAttachedLabel: String,
    val recordVoiceNoteButton: String,
    val stopVoiceRecordingButton: String,
    val voiceNoteAttachedLabel: String,
    val tagsLabel: String,
    val tagsPlaceholder: String,
    val backupSectionTitle: String,
    val exportJsonButton: String,
    val exportTextButton: String,
    val importJsonButton: String,
    val importDialogTitle: String,
    val importJsonPlaceholder: String,
    val themeSectionTitle: String,
    val languageSectionTitle: String,
    val autoLockTimeoutLabel: String,
    val aesKeystoreBadge: String,
    val cancel: String,
    val confirm: String,
    val clear: String,
    val copyToClipboard: String,
    val copiedToast: String,
    val syncedBadge: String,
    val selectNotePrompt: String,
    val hintPrefix: String,
    val untitledNote: String,
    val firebaseErrorDefault: String
) {
    fun categoryName(categoryKey: String): String {
        return when (categoryKey.lowercase()) {
            NoteCategory.ALL.key -> if (isArabic) "الكل" else "All"
            NoteCategory.PERSONAL.key -> if (isArabic) "شخصي" else "Personal"
            NoteCategory.WORK.key -> if (isArabic) "عمل" else "Work"
            NoteCategory.IDEAS.key -> if (isArabic) "أفكار" else "Ideas"
            NoteCategory.JOURNAL.key -> if (isArabic) "يوميات" else "Journal"
            NoteCategory.SECRET.key -> if (isArabic) "سري" else "Secret"
            else -> categoryKey
        }
    }

    fun sortOptionName(option: NoteSortOption): String {
        return when (option) {
            NoteSortOption.MODIFIED_DESC -> if (isArabic) "تاريخ التعديل" else "Date Modified"
            NoteSortOption.CREATED_DESC -> if (isArabic) "تاريخ الإنشاء" else "Creation Date"
            NoteSortOption.TITLE_ASC -> if (isArabic) "العنوان (أ-ي)" else "Title (A-Z)"
            NoteSortOption.COLOR -> if (isArabic) "اللون" else "Color"
            NoteSortOption.PINNED -> if (isArabic) "المثبتة أولاً" else "Pinned First"
        }
    }

    fun syncStatusName(status: SyncStatus): String {
        return when (status) {
            SyncStatus.SYNCED -> if (isArabic) "متزامن" else "Synced"
            SyncStatus.SYNCING -> if (isArabic) "جارٍ المزامنة..." else "Syncing..."
            SyncStatus.OFFLINE -> if (isArabic) "يعمل دون اتصال" else "Offline Mode"
            SyncStatus.ERROR -> if (isArabic) "خطأ في المزامنة" else "Sync Error"
        }
    }

    fun timeoutName(timeout: AutoLockTimeout): String {
        return when (timeout) {
            AutoLockTimeout.IMMEDIATELY -> if (isArabic) "فوراً" else "Immediately"
            AutoLockTimeout.ONE_MINUTE -> if (isArabic) "بعد دقيقة واحدة" else "After 1 min"
            AutoLockTimeout.FIVE_MINUTES -> if (isArabic) "بعد 5 دقائق" else "After 5 min"
            AutoLockTimeout.FIFTEEN_MINUTES -> if (isArabic) "بعد 15 دقيقة" else "After 15 min"
        }
    }

    fun themeModeName(mode: ThemeMode): String {
        return when (mode) {
            ThemeMode.SYSTEM -> if (isArabic) "تلقائي (النظام)" else "System"
            ThemeMode.LIGHT -> if (isArabic) "فاتح" else "Light"
            ThemeMode.DARK -> if (isArabic) "داكن" else "Dark"
        }
    }

    fun languagePrefName(pref: LanguagePreference): String {
        return when (pref) {
            LanguagePreference.ARABIC -> "العربية"
            LanguagePreference.ENGLISH -> "English"
            LanguagePreference.SYSTEM -> if (isArabic) "لغة الجهاز" else "System"
        }
    }
}

val ArabicStrings = VaultStrings(
    isArabic = true,
    appTitle = "ملاحظاتي العربية",
    appSubtitle = "ملاحظاتك الخاصة متزامنة ومشفرة عبر جميع أجهزتك",
    welcomeHeadline = "مساحتك العربية الخاصة والمشفرة",
    welcomeDescription = "اكتب ونظّم ملاحظاتك وقوائم مهامك وصورك ومذكراتك الصوتية مع دعم كامل للغة العربية (RTL)، ومزامنة سحابية فورية، وتشفير AES-256 مع قفل التطبيق والملاحظات.",
    featureSyncTitle = "مزامنة سحابية والعمل دون اتصال",
    featureSyncDesc = "تعمل ملاحظاتك بدون إنترنت عبر قاعدة بيانات Room وتزامن تلقائياً مع Cloud Firestore",
    featureLockTitle = "تشفير وقفل بالبصمة أو الرمز السري",
    featureLockDesc = "احمِ التطبيق بالكامل أو اقفل ملاحظات فردية بتشفير Android Keystore",
    featureArabicTitle = "مصمم للغة العربية أولاً",
    featureArabicDesc = "خط القاهرة (Cairo) الأصيل وتخطيط كامل من اليمين إلى اليسار مع دعم الإنجليزية",
    signInWithGoogle = "تسجيل الدخول باستخدام Google",
    signingIn = "جارٍ تسجيل الدخول...",
    signOut = "تسجيل الخروج",
    searchPlaceholder = "ابحث في العناوين، المحتوى، الوسوم، أو المهام...",
    newNote = "ملاحظة جديدة",
    editNote = "تعديل الملاحظة",
    noteTitlePlaceholder = "عنوان الملاحظة...",
    noteContentPlaceholder = "اكتب أفكارك وملاحظاتك هنا (يدعم العربية والإنجليزية)...",
    saveNote = "حفظ الملاحظة",
    deleteNote = "حذف",
    moveToTrash = "نقل إلى المهملات",
    restoreFromTrash = "استعادة الملاحظة",
    deletePermanently = "حذف نهائي",
    emptyTrashButton = "إفراغ سلة المهملات",
    trashAutoDeleteBanner = "يتم حذف الملاحظات في سلة المهملات تلقائياً بعد 30 يوماً.",
    archiveNote = "أرشفة",
    unarchiveNote = "إلغاء الأرشفة",
    pinNote = "تثبيت في الأعلى",
    unpinNote = "إلغاء التثبيت",
    favoriteNote = "إضافة للمفضلة",
    unfavoriteNote = "إزالة من المفضلة",
    pinnedSection = "الملاحظات المثبتة",
    allNotesSection = "جميع الملاحظات",
    favoritesSection = "المفضلة",
    archivedSection = "الأرشيف",
    trashSection = "سلة المهملات",
    emptyNotesTitle = "لا توجد ملاحظات بعد",
    emptyNotesSubtitle = "اضغط على زر + لإضافة أول ملاحظة خاصة متزامنة عبر أجهزتك",
    emptyArchivedTitle = "لا توجد ملاحظات في الأرشيف",
    emptyFavoritesTitle = "لا توجد ملاحظات مفضلة بعد",
    emptyTrashTitle = "سلة المهملات فارغة",
    lockedNoteLabel = "ملاحظة مقفلة ومشفرة (AES-256)",
    tapToUnlockNote = "اضغط لفتح القفل وعرض المحتوى المشفر",
    lockNoteAction = "قفل وتشفير الملاحظة",
    unlockNoteAction = "فتح الملاحظة",
    removeNoteLockAction = "إلغاء قفل الملاحظة",
    appLockedTitle = "التطبيق مقفل لحمايتك",
    appLockedSubtitle = "استخدم قفل الجهاز / البصمة أو أدخل الرمز السري المكون من 4 أرقام",
    unlockWithBiometricOrDevice = "فتح باستخدام بصمة / قفل الجهاز",
    enterPinPrompt = "أدخل الرمز السري (4 أرقام)",
    enterNewPinPrompt = "اختر رمزاً سرياً من 4 أرقام",
    confirmPinPrompt = "أعد إدخال الرمز للتأكيد",
    pinHintLabel = "تلميح الرمز السري (اختياري)",
    pinHintPlaceholder = "مثال: سنة التخرج",
    invalidPinError = "الرمز السري غير صحيح، حاول مرة أخرى",
    pinTooShortError = "يرجى إدخال 4 أرقام كاملة",
    pinMismatchError = "الرمزان غير متطابقين",
    securitySettingsTitle = "الإعدادات، الأمان والنسخ الاحتياطي",
    appLockToggleLabel = "قفل التطبيق تلقائياً",
    appLockToggleDesc = "طلب البصمة أو رمز PIN عند فتح التطبيق لحماية خصوصيتك",
    changeAppPinButton = "تعيين / تغيير رمز PIN الاحتياطي",
    setAppPinTitle = "إعداد رمز قفل التطبيق",
    setNotePinTitle = "قفل وتشفير هذه الملاحظة",
    unlockNoteTitle = "فتح الملاحظة المقفلة",
    lockVaultNow = "قفل الآن",
    activeViewNotes = "الملاحظات",
    activeViewFavorites = "المفضلة",
    activeViewArchive = "الأرشيف",
    activeViewTrash = "المهملات",
    colorLabel = "لون البطاقة",
    categoryLabel = "التصنيف",
    sortByLabel = "ترتيب حسب",
    viewModeGrid = "عرض شبكي",
    viewModeList = "عرض قائمة",
    checklistTitle = "قائمة المهام الفرعية",
    addChecklistItemPlaceholder = "أضف مهمة جديدة...",
    addChecklistButton = "إضافة",
    reminderLabel = "التذكير",
    reminderPlaceholder = "مثال: غداً الساعة 9:00 صباحاً",
    attachPhotoButton = "إرفاق صورة",
    photoAttachedLabel = "صورة مرفقة",
    recordVoiceNoteButton = "تسجيل ملاحظة صوتية",
    stopVoiceRecordingButton = "إيقاف التسجيل",
    voiceNoteAttachedLabel = "مذكرة صوتية مرفقة",
    tagsLabel = "الوسوم (Tags)",
    tagsPlaceholder = "مفصولة بفاصلة: عمل، أفكار، هام",
    backupSectionTitle = "النسخ الاحتياطي والتصدير (JSON / نص)",
    exportJsonButton = "تصدير كملف JSON",
    exportTextButton = "تصدير كنص عادي",
    importJsonButton = "استيراد من JSON",
    importDialogTitle = "نسخ احتياطي / استيراد الملاحظات",
    importJsonPlaceholder = "الصق محتوى JSON هنا للاستيراد...",
    themeSectionTitle = "المظهر (Theme)",
    languageSectionTitle = "لغة التطبيق",
    autoLockTimeoutLabel = "مهلة القفل التلقائي",
    aesKeystoreBadge = "تشفير محلي AES-256 Keystore",
    cancel = "إلغاء",
    confirm = "تأكيد",
    clear = "مسح",
    copyToClipboard = "نسخ إلى الحافظة",
    copiedToast = "تم النسخ إلى الحافظة بنجاح",
    syncedBadge = "متزامن سحابياً بأمان",
    selectNotePrompt = "اختر ملاحظة من القائمة أو أنشئ ملاحظة جديدة",
    hintPrefix = "تلميح: ",
    untitledNote = "ملاحظة بدون عنوان",
    firebaseErrorDefault = "تعذر إتمام العملية السحابية. تم حفظ التغييرات محلياً وستتم المزامنة عند توفر الاتصال."
)

val EnglishStrings = VaultStrings(
    isArabic = false,
    appTitle = "Arabic Notes",
    appSubtitle = "Offline & cloud-synced notes with biometric/PIN lock & encryption",
    welcomeHeadline = "Your Private Bilingual Note Sanctuary",
    welcomeDescription = "Write, organize, and sync rich notes, checklists, photos, and voice memos with native RTL Arabic & English support, offline Room persistence, and AES-256 Keystore protection.",
    featureSyncTitle = "Offline-First + Cloud Sync",
    featureSyncDesc = "Works seamlessly offline with Room and syncs automatically to Cloud Firestore",
    featureLockTitle = "Biometric, PIN & AES-256 Encryption",
    featureLockDesc = "Lock the entire app or encrypt individual notes using Android Keystore",
    featureArabicTitle = "Arabic-First RTL & English",
    featureArabicDesc = "Crafted with Cairo typography and instant Arabic/English/System switching",
    signInWithGoogle = "Sign in with Google",
    signingIn = "Signing in...",
    signOut = "Sign Out",
    searchPlaceholder = "Search titles, content, tags, or checklists...",
    newNote = "New Note",
    editNote = "Edit Note",
    noteTitlePlaceholder = "Note title...",
    noteContentPlaceholder = "Write your thoughts here (supports Arabic & English)...",
    saveNote = "Save Note",
    deleteNote = "Delete",
    moveToTrash = "Move to Trash",
    restoreFromTrash = "Restore Note",
    deletePermanently = "Delete Forever",
    emptyTrashButton = "Empty Trash",
    trashAutoDeleteBanner = "Notes in Trash are automatically deleted after 30 days.",
    archiveNote = "Archive",
    unarchiveNote = "Unarchive",
    pinNote = "Pin to top",
    unpinNote = "Unpin",
    favoriteNote = "Add to Favorites",
    unfavoriteNote = "Remove Favorite",
    pinnedSection = "Pinned Notes",
    allNotesSection = "All Notes",
    favoritesSection = "Favorites",
    archivedSection = "Archived Notes",
    trashSection = "Trash",
    emptyNotesTitle = "No Notes Yet",
    emptyNotesSubtitle = "Tap + to create your first offline & cloud-synced note",
    emptyArchivedTitle = "No archived notes",
    emptyFavoritesTitle = "No favorite notes yet",
    emptyTrashTitle = "Trash is empty",
    lockedNoteLabel = "Encrypted & Locked Note (AES-256)",
    tapToUnlockNote = "Tap to authenticate and decrypt note",
    lockNoteAction = "Lock & encrypt note",
    unlockNoteAction = "Unlock Note",
    removeNoteLockAction = "Remove Note Lock",
    appLockedTitle = "Arabic Notes Locked",
    appLockedSubtitle = "Authenticate with Device/Biometric credential or your 4-digit PIN",
    unlockWithBiometricOrDevice = "Unlock with Biometric / Device Lock",
    enterPinPrompt = "Enter 4-digit PIN",
    enterNewPinPrompt = "Choose a 4-digit PIN",
    confirmPinPrompt = "Re-enter PIN to confirm",
    pinHintLabel = "Optional PIN Hint",
    pinHintPlaceholder = "e.g., Graduation year",
    invalidPinError = "Incorrect PIN. Please try again.",
    pinTooShortError = "Please enter all 4 digits",
    pinMismatchError = "PIN codes do not match",
    securitySettingsTitle = "Settings, Security & Backup",
    appLockToggleLabel = "App Biometric / PIN Lock",
    appLockToggleDesc = "Require authentication when launching Arabic Notes",
    changeAppPinButton = "Set / Change Fallback PIN",
    setAppPinTitle = "Set App Lock PIN",
    setNotePinTitle = "Lock & Encrypt Note",
    unlockNoteTitle = "Unlock Encrypted Note",
    lockVaultNow = "Lock Now",
    activeViewNotes = "Notes",
    activeViewFavorites = "Favorites",
    activeViewArchive = "Archive",
    activeViewTrash = "Trash",
    colorLabel = "Card Color",
    categoryLabel = "Category",
    sortByLabel = "Sort by",
    viewModeGrid = "Grid View",
    viewModeList = "List View",
    checklistTitle = "Checklist Items",
    addChecklistItemPlaceholder = "Add checklist item...",
    addChecklistButton = "Add",
    reminderLabel = "Reminder",
    reminderPlaceholder = "e.g., Tomorrow at 9:00 AM",
    attachPhotoButton = "Attach Photo",
    photoAttachedLabel = "Photo Attached",
    recordVoiceNoteButton = "Record Voice Memo",
    stopVoiceRecordingButton = "Stop Recording",
    voiceNoteAttachedLabel = "Voice Memo Attached",
    tagsLabel = "Tags",
    tagsPlaceholder = "Comma-separated: work, ideas, urgent",
    backupSectionTitle = "Backup & Restore (JSON / Text)",
    exportJsonButton = "Export as JSON",
    exportTextButton = "Export as Text",
    importJsonButton = "Import from JSON",
    importDialogTitle = "Backup / Import Notes",
    importJsonPlaceholder = "Paste exported JSON array here...",
    themeSectionTitle = "App Theme",
    languageSectionTitle = "App Language",
    autoLockTimeoutLabel = "Auto-Lock Timeout",
    aesKeystoreBadge = "AES-256 Android Keystore",
    cancel = "Cancel",
    confirm = "Confirm",
    clear = "Clear",
    copyToClipboard = "Copy to Clipboard",
    copiedToast = "Copied to clipboard",
    syncedBadge = "Cloud Synced & Encrypted",
    selectNotePrompt = "Select a note from the list or create a new one",
    hintPrefix = "Hint: ",
    untitledNote = "Untitled Note",
    firebaseErrorDefault = "Cloud sync error. Changes are saved locally and will sync when back online."
)

val LocalVaultStrings = staticCompositionLocalOf { ArabicStrings }
