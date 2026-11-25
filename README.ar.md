<a id="readme-top"></a>

<div align="center">
  <a href="README.md">English</a> &nbsp;|&nbsp; <b>العربية</b>
</div>

<!-- PROJECT SHIELDS -->
<div align="center">

[![Stargazers][stars-shield]][stars-url]
[![Forks][forks-shield]][forks-url]
[![Issues][issues-shield]][issues-url]

</div>

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio">
    <img src="docs/images/logo.png" alt="ps3mio logo" width="200" height="200">
  </a>

  <h3 align="center">ps3mio</h3>

  <p align="center">
    Stremio على PlayStation 3 (وعلى أندرويد للتجربة).
    <br />
    <br />
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/actions">البناءات</a>
    &middot;
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues">الإبلاغ عن خطأ</a>
    &middot;
    <a href="https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues">طلب ميزة</a>
  </p>
</div>

<div dir="rtl">

## عن المشروع

[![لقطة من ps3mio][product-screenshot]](#عن-المشروع)

يجلب ps3mio تجربة Stremio إلى PlayStation 3. التطبيق مبني على محرك
[Movian](https://github.com/andoma/movian)، وهو خفيف بما يكفي لعتاد PS3 القديم. يوفّر Movian المشغل
(مع فك الفيديو بعتاد PS3) والتورنت والشبكة، أما كل ما تراه فهو Stremio:

* واجهة بنمط Stremio: Board وDiscover وLibrary وSearch وAddons وSettings
* تسجيل الدخول بحساب Stremio (برمز ربط أو بالبريد) واستخدام إضافاتك أنت
* متابعة المشاهدة، المواسم والحلقات، وقائمة روابط تعرض عدد الـ seeders وحجم الملف
* ترجمات من إضافاتك، بما فيها WebVTT
* شاشة تحميل بشعار العمل تمتلئ تدريجياً أثناء تعبئة الـ buffer

الواجهة ومنطق Stremio ودعم الإضافات كلها مدمجة داخل التطبيق، وتُطوَّر وتُبنى معاً كمشروع واحد.

### المبني به

* [Movian](https://github.com/andoma/movian) (المحرك)
* JavaScript
* GitHub Actions

## البدء

### البناءات

تُشغَّل البناءات من تبويب **Actions** في المستودع.

| Workflow | الناتج | التشغيل |
|---|---|---|
| Engine - Android APK | `ps3mio-engine.apk` | تلقائي عند كل رفع يمس `engine/` |
| Engine - PS3 PKG | `ps3mio.pkg` و `ps3mio_geohot.pkg` | يدوي |

نسخة أندرويد هي نفس المحرك والمشغل والواجهة الموجودة في نسخة PS3، لذلك تُستخدم لتجربة التعديلات بسرعة.

### هيكل المشروع

* `engine/skin/`: واجهة ps3mio (شريط التنقل، Board، Discover، Library، صفحة العمل، شاشة التحميل)
* `engine/plugin/`: منطق Stremio المدمج (API، الإضافات، الإعدادات)
* `engine/ps3mio_patch.py`: يحوّل شجرة Movian إلى ps3mio
* `docs/`: ملاحظات التصميم

## خارطة الطريق

- [x] واجهة Stremio فوق محرك Movian
- [x] تسجيل الدخول بحساب Stremio ومزامنة الإضافات
- [x] ترجمات WebVTT من الإضافات
- [x] خط عربي وإنجليزي مع وصل الحروف العربية واتجاه الكتابة من اليمين لليسار
- [x] buffer بطول 10 ثوانٍ قبل بدء التشغيل وبعد كل انقطاع
- [ ] إعادة تصميم قائمة المشغل لتطابق Stremio
- [ ] إعدادات الترجمة (الحجم والألوان) داخل صفحة إعدادات Stremio
- [ ] التجربة على جهاز PS3 حقيقي

## تاريخ النجوم

</div>

<a href="https://star-history.com/#YOUR_GITHUB_USERNAME/ps3mio&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date" />
    <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=YOUR_GITHUB_USERNAME/ps3mio&type=Date" />
  </picture>
</a>

<div dir="rtl">

## الرخصة

ps3mio مبني على Movian الموزَّع برخصة GPL-3.0. راجع مستودع Movian لمعرفة التفاصيل.

## شكر وتقدير

* [Movian](https://github.com/andoma/movian) لـ Andreas Öman والمساهمين
* [Stremio](https://www.stremio.com): الواجهة وبروتوكول الإضافات الذي يتبعه ps3mio
* [Best-README-Template](https://github.com/othneildrew/Best-README-Template)
* [Img Shields](https://shields.io) و[Star History](https://star-history.com)

<p align="left">(<a href="#readme-top">العودة للأعلى</a>)</p>

</div>

[forks-shield]: https://img.shields.io/github/forks/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[forks-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/network/members
[stars-shield]: https://img.shields.io/github/stars/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[stars-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/stargazers
[issues-shield]: https://img.shields.io/github/issues/YOUR_GITHUB_USERNAME/ps3mio.svg?style=for-the-badge
[issues-url]: https://github.com/YOUR_GITHUB_USERNAME/ps3mio/issues
[product-screenshot]: docs/images/screenshot.png
