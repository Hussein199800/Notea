# Notea

تطبيق ملاحظات عربي الهوية: سريع، أنيق، يعمل دون إنترنت، مع تشفير حقيقي للملاحظات المقفلة، واستيراد من Samsung / Xiaomi / Huawei، ونسخ احتياطي بصيغة notea.

تصميم وتطوير: **حسين الطويل** (Hussein Taweel).

## البناء
```bash
./gradlew :app:assembleDebug        # نسخة تجريبية
./gradlew :app:assembleRelease      # تحتاج مفتاح التوقيع (انظر أدناه)
./gradlew :app:testDebugUnitTest    # الاختبارات
```

### مفتاح التوقيع
انسخ `keystore.properties.example` إلى `keystore.properties` وضع ملف `notea-release.jks` في جذر المشروع. الملفان لا يُرفعان إلى git أبداً. احتفظ بنسخة آمنة منهما: فقدانهما يعني عدم القدرة على نشر تحديثات للتطبيق نفسه.

### المزامنة السحابية (اختيارية)
بدون Firebase يعمل التطبيق بالكامل على الجهاز. لتفعيل تسجيل الدخول والمزامنة:
1. أنشئ مشروع Firebase وأضف تطبيق Android بالمعرّف `com.notea.app` وبصمة SHA-1 لمفتاحك.
2. فعّل Authentication (Google) و Firestore.
3. ضع `google-services.json` في `app/` وانشر `firestore.rules`.
عند تسجيل الدخول تنتقل الملاحظات المحلية إلى الحساب تلقائياً.
