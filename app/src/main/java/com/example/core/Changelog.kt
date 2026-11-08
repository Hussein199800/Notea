package com.example.core

/**
 * Release notes shown in «ما الجديد» and the releases page. The newest entry must match
 * versionName in app/build.gradle.kts and the top of release-notes.md (VersionMatchTest).
 */
object Changelog {
    data class Release(val version: String, val date: String, val ar: List<String>, val en: List<String>)

    val releases = listOf(
        Release(
            "1.2.0", "2026-11-08",
            ar = listOf(
                "أربعة تبويبات: الملاحظات، المهام والتذكيرات، المجلدات والوسوم، المزيد",
                "تبويب المهام يجمع عناصر القوائم غير المنجزة والتذكيرات القادمة من كل ملاحظاتك",
                "تحديد عدة ملاحظات بالضغط المطوّل، وزر «تراجع» بعد كل حذف",
                "قراءة نص الصور أدق بالعربية، ولا يُعرض النص غير الواضح",
                "حجم الخط من الإعدادات، وواجهة مكبّرة مناسبة للأجهزة اللوحية",
                "الأرقام اللاتينية داخل النص العربي، وحقول الأرقام تقبل الأرقام العربية أيضاً",
                "جولة تعريفية، وصفحة الإصدارات، وسجل تشخيص يخفي البيانات الحساسة",
                "التحقق من التحديثات من GitHub مع فحص التوقيع قبل التثبيت"
            ),
            en = listOf(
                "Four tabs: Notes, Tasks & reminders, Folders & tags, More",
                "The Tasks tab gathers open checklist items and upcoming reminders from all notes",
                "Select several notes with a long press, and Undo after every delete",
                "More accurate Arabic text recognition; unclear text is no longer shown",
                "Text size setting and a scaled layout for tablets",
                "Latin digits inside Arabic text; number fields accept Arabic digits",
                "A short tour, a releases page and a diagnostics log that masks sensitive data",
                "Update check from GitHub with a signature check before installing"
            )
        ),
        Release(
            "1.1.3", "2026-11-07",
            ar = listOf("إصلاح توقف التطبيق عند فتح الكاميرا بدل الماسح", "عزل قراءة النص في عملية مستقلة مع تقرير أعطال"),
            en = listOf("Fixed a crash when the scanner falls back to the camera", "Text recognition runs in its own process, with a crash report")
        ),
        Release(
            "1.1.0", "2026-11-05",
            ar = listOf("محرر موحّد بالكتل: نص وقوائم ومرفقات", "قراءة نص الصور على الجهاز وجعلها قابلة للبحث", "سجل الإصدارات مع المقارنة", "مركز الترحيل والنقل بين هاتفين", "المساعد الذكي بمفتاحك الخاص وذاكرة Notea"),
            en = listOf("Unified block editor: text, checklists and attachments", "On-device text recognition, searchable", "Version history with diff", "Migration center and phone-to-phone transfer", "AI assistant with your own key, and Notea Memory")
        )
    )

    val current get() = releases.first()
}
