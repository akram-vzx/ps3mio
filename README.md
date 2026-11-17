# ps3mio

Stremio لجهاز PS3، مع نسخة أندرويد للتجارب. التصميم وقرارات اللغة والمشغل والتورنت في `docs/ARCHITECTURE.md`.

## الحالة
- `android/`: تطبيق Kotlin كامل لنقاط التجربة (Board / Discover / Library / Search / Addons / Settings / تفاصيل / روابط / مشغل ExoPlayer).
  لم يُبنَ بعد؛ أول بناء قد يكشف أخطاء ترجمة سأصلحها من سجل GitHub.
- `ps3/`: مرحلة التصميم فقط، ولا يوجد كود PS3 بعد.
- التورنت غير مضمَّن في نسخة أندرويد الأولى؛ تظهر روابطه (Seeds والحجم) لكن تشغيله يحتاج المحرك (المرحلة D).

## البناء
Workflow **Android APK** يعمل تلقائياً عند كل push يمس `android/`، أو يدوياً من تبويب Actions.
الناتج: Artifact باسم `ps3mio-apk` (ملف `ps3mio.apk`). Workflow **PS3** غير فعّال بعد ويُشغَّل يدوياً فقط.

## الأيقونة
`docs/icon-source.svg` (الأصل) ومنه ولّدتُ أيقونات `android/app/src/main/res/mipmap-*`.
