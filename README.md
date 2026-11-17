# ps3mio

Stremio لجهاز PS3، مع نسخة أندرويد للتجارب. التصميم ونتائج البحث في `docs/ARCHITECTURE.md`.

## مسارا المشروع
| المجلد | ما هو | يعمل على PS3؟ |
|---|---|---|
| `android/` | تطبيق Kotlin + ExoPlayer لتجربة الواجهة ومنطق Stremio وإعدادات المشغل على الهاتف | لا |
| `engine/` | محرك Movian مع منطق Stremio مدمج (دون تثبيت إضافة)، يُبنى لأندرويد ولـ PS3 بنفس المشغل | نعم |

## البناء (تبويب Actions)
| Workflow | الناتج | التشغيل |
|---|---|---|
| Android APK | `ps3mio.apk` (Kotlin) | تلقائي عند الرفع |
| Engine - Android APK | `ps3mio-engine.apk` (نفس محرك PS3) | يدوي |
| Engine - PS3 PKG | `ps3mio.pkg` و `ps3mio_geohot.pkg` | يدوي |

## حالة الشيفرة
لم يُبنَ شيء من هذا على جهاز؛ المختبَر فقط: منطق إضافة Stremio (بمحاكاة Movian)، وأن تعديلات C تُترجم بدون أخطاء.
تطبيق Kotlin لم يُترجم بعد. بناء المحرك طويل وقد يحتاج إصلاحات من السجل.

## مجلدات إضافات خارجية (للتطوير دون إعادة بناء)
أندرويد: `Download/ps3mio/` أو `Download/m7/`، وPS3: `/dev_usb000/ps3mio/` أو `/dev_hdd0/ps3mio/`.
ضع فيها `stremio.zip` ليتقدم على النسخة المدمجة، ثم Settings > Plugins > Reload.
