# Universe Stream — تقرير إعادة العلامة التجارية وإعادة الهيكلة

## الملخص التنفيذي

تم استنساخ مستودع StreamVault-IPTV وتحويل هوية المشروع البرمجية والبصرية إلى **Universe Stream**. شملت التغييرات package/application ID، مسارات الحزم، أسماء الأصناف والملفات ذات الصلة، النصوص متعددة اللغات، لوحة الألوان، موارد Manifest، وأصول الشعار الجديدة. حافظت التعديلات على طبقات Xtream Codes وEPG وMedia3/ExoPlayer الموجودة في المشروع بدل إعادة اختراعها أو تعطيلها.

## التغييرات المنفذة

| المجال | النتيجة |
| --- | --- |
| Application ID وnamespace | `com.universestream.app` في Gradle وManifest ومصادر Kotlin/Java |
| اسم التطبيق | `Universe Stream` في موارد `values*`، مع إبقاء أسماء بروتوكول Xtream وEPG الوظيفية كما هي |
| إعادة تسمية الحزم | نقل المصادر من `com.streamvault` إلى `com.universestream` في وحدات `app` و`data` و`domain` و`player` والاختبارات |
| إعادة تسمية الأصناف | تحويل الأسماء التجارية المضمّنة مثل `StreamVaultTvInputService` و`StreamVaultCastOptionsProvider` إلى `UniverseStream...` |
| الألوان | الخلفية `#120A1A`، السطح الملكي `#3D1E60`، accent سماوي كهربائي `#00E5FF`، وتدرجات بنفسجية محسّنة |
| الأصول | إضافة `universe_stream_logo.png` و`universe_stream_splash.png` وربطهما بالأيقونة وTV banner، وتحديث adaptive foreground |
| Manifest | تحديث `android:icon` و`android:roundIcon` و`android:banner` وTV input label إلى Universe Stream |
| الخطوط | الإبقاء على نظام Typography الحالي المبني على Compose/Roboto-compatible system fonts مع دعم موارد العربية الموجودة |
| التكاملات | لم تُحذف Xtream Codes أو XMLTV/EPG أو preview playback أو Media3/ExoPlayer؛ بقيت واجهات الإعداد والتنقل والدليل قائمة |
| بيئة البناء | تثبيت JDK 17 و21 وAndroid SDK API 36 وBuild Tools 35 محليًا، وإضافة `local.properties` غير المتعقب |
| التوقيع | إنشاء keystore محلي غير متعقب باسم `universe-stream-release.jks` و`keystore.properties` لاستخدامه مع release |

## نتائج التحقق

تم التحقق من عدم وجود مراجع مصدرية متبقية إلى `com.streamvault` أو `com/streamvault` أو صيغ العلامة التجارية القديمة داخل الوحدات البرمجية والموارد التي تمت مراجعتها. كما تم تشغيل Gradle بعد تهيئة SDK وتثبيت JDK المطلوب.

حاول البناء إصدار Debug ثم إصدار Release موقّع. اجتاز المشروع مراحل إعداد SDK، معالجة الموارد، KSP، وتجميع أجزاء من الوحدات، إلا أن بيئة التنفيذ ذات الذاكرة المحدودة أوقفت Kotlin compiler بسبب:

> `java.lang.OutOfMemoryError: GC overhead limit exceeded`

وبالتالي **لم يتم إنتاج APK نهائي يمكن تسليمه من هذه الجلسة**. المشكلة ظهرت أثناء `:data:compileReleaseKotlin` في المحاولة منخفضة الذاكرة، وليست رسالة خطأ في package/application ID أو الموارد المعاد تسميتها. يلزم تشغيل البناء في بيئة Android/CI بذاكرة أكبر، ويفضّل 8 GB RAM على الأقل، ثم تنفيذ `./gradlew :app:assembleRelease` باستخدام ملف `keystore.properties` المناسب.

## ملاحظات التوقيع

تم إنشاء مفتاح توقيع محلي للاختبار/التوزيع الداخلي، لكنه لم يُضمّن في الأرشيف أو Git. يجب تغيير كلمة مرور المفتاح وإنشاء keystore مؤسسي جديد قبل أي نشر عام على Google Play أو أي قناة إنتاجية. لا ينبغي اعتبار نسخة release الناتجة مستقبلًا موقعة إنتاجيًا إلا بعد اعتماد keystore وسياسة إدارة أسرار رسمية.

## الملفات المهمة

| الملف | الغرض |
| --- | --- |
| `app/src/main/java/com/universestream/app/ui/design/AppColors.kt` | لوحة ألوان Universe Stream |
| `app/src/main/res/drawable/universe_stream_logo.png` | شعار الأيقونة |
| `app/src/main/res/drawable/universe_stream_splash.png` | شاشة الترحيب/TV banner |
| `app/src/main/AndroidManifest.xml` | تعريف الهوية والأيقونات وTV input |
| `app/build.gradle.kts` | package/application ID وتكوين release signing الموجود |
| `gradle.properties` | إعدادات Gradle منخفضة الذاكرة المستخدمة للتحقق |

## الخلاصة

التعديلات المصدرية والهوية البصرية الأساسية مكتملة، والتكاملات الوظيفية الرئيسية بقيت محفوظة. التسليم النهائي يضم الكود المعدّل والأصول والتقرير، مع توضيح أن إنشاء APK موقّع تعذر بسبب حد الذاكرة في بيئة البناء الحالية وليس بسبب فشل إعادة التسمية أو تضارب اعتماديات مكتشف في السجل الأخير.

**المؤلف:** Manus AI  
**التاريخ:** 16 أغسطس 2026
