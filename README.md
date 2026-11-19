# ps3mio

Stremio على PS3 (وأندرويد للتجربة). التطبيق = محرك Movian (المشغل والتورنت والشبكة) فقط، أما الواجهة والإعدادات
والصفحة الرئيسية فهي Stremio: سكن مخصص ومنطق Stremio مدمج في الملف التنفيذي. لا شيء من واجهة Movian أو إعداداته.

## الملفات
- `engine/skin/`: سكن ps3mio (شريط التنقل، Board، Discover، Library، صفحة العمل، شاشة التحميل بشعار العمل).
- `engine/plugin/`: منطق Stremio (API، الـ addons، الإعدادات) المدمج في التطبيق.
- `engine/ps3mio_patch.py`: يحوّل شجرة Movian إلى ps3mio (الصفحة الرئيسية = Stremio، حذف فحوص التحديث والإحصاءات، إصلاح تشغيل التورنت، إعدادات المشغل، دمج السكن والمنطق).

## البناء (تبويب Actions)
| Workflow | الناتج | التشغيل |
|---|---|---|
| Engine - Android APK | `ps3mio-engine.apk` | تلقائي عند كل رفع يمس `engine/` |
| Engine - PS3 PKG | `ps3mio.pkg` و `ps3mio_geohot.pkg` | يدوي |

## مجلدات للتطوير (اختياري)
ضع `stremio.zip` في `Download/ps3mio/` (أندرويد) أو `/dev_usb000/ps3mio/` (PS3) ليتقدم على النسخة المدمجة.
