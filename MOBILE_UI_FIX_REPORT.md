# تقرير إصلاحات Mobile UI والوظائف

## نطاق الإصدار

تم تنفيذ هذه الجولة على commit `99558a8c6201db54fdc9d0aabfdd3a7f23a80712` في الفرع `main` لمشروع **Universe Stream**. يركز الإصدار على الهاتف واللوحي مع إبقاء مسار Android TV منفصلاً وغير متأثر.

| البند | النتيجة |
|---|---|
| Commit | `99558a8` — `refine compact mobile UI and provider persistence` |
| CI | Build 27 — ناجح |
| GitHub Actions | [Run 32021526696](https://github.com/untv22-dev/Universe-Stream/actions/runs/32021526696) |
| Release | [Universe Stream CI Build 27](https://github.com/untv22-dev/Universe-Stream/releases/tag/ci-build-27) |
| APK | [تحميل app-release.apk](https://github.com/untv22-dev/Universe-Stream/releases/download/ci-build-27/app-release.apk) |
| حجم APK | 25,349,070 بايت |
| SHA-256 | `3e345d97420c71270bb5f35758f76c9773ef72cc00a10ca8061e14f05ebc567e` |

> نفّذ CI بنجاح مراحل بناء release الموقّع، والتحقق من توقيع APK، ونشره في GitHub Release. تم تنزيل asset باستخدام GitHub CLI وفحصه بنجاح بواسطة `unzip -t`.

## الملفات التي تغيرت في هذا الإصدار

| الملف | الغرض من التغيير |
|---|---|
| `app/src/main/java/com/universestream/app/ui/screens/home/MobileLiveTvContent.kt` | اختيار فئة All Channels الحقيقية، إصلاح اختيار All داخل الحوار، اختيار All تلقائياً عند غياب الفئة المحددة، وفصل حالات تحميل الفئات والقنوات مع fallback للحالة الفارغة. |
| `app/src/main/java/com/universestream/app/ui/screens/provider/ProviderSetupScreen.kt` | استعادة مسودة Xtream في شاشة الإضافة فقط، وإصلاح لمس زر إظهار/إخفاء كلمة المرور على الهاتف مع عدم تعطيل تفاعل التلفزيون. |
| `app/src/main/java/com/universestream/app/ui/screens/provider/ProviderSetupViewModel.kt` | ربط DataStore والتشفير، قراءة المسودة واستعادتها في الذاكرة، وحفظ بيانات Xtream بعد نجاح المصادقة فقط. |
| `data/src/main/java/com/universestream/data/preferences/PreferencesRepository.kt` | إضافة مفاتيح وواجهات DataStore لمسودة Xtream، مع حفظ كلمة المرور بصيغة مشفرة. |
| `app/src/main/java/com/universestream/app/ui/screens/settings/SettingsScreen.kt` | إضافة فرع Compact أحادي العمود، مع إبقاء rail وside panel لمسار التلفزيون. |
| `app/src/main/java/com/universestream/app/ui/screens/settings/SettingsContentPane.kt` | إضافة padding اختياري؛ القيمة الافتراضية لمسار TV بقيت كما هي. |
| `app/src/main/java/com/universestream/app/ui/screens/settings/MobileProvidersContent.kt` | واجهة Providers للهاتف ببطاقات أحادية العمود تعرض الاسم والنوع والحالة والانتهاء وإجراء الإدارة. |
| `app/src/main/java/com/universestream/app/ui/screens/settings/MobileSettingsCategoryBar.kt` | شريط أقسام إعدادات Compact قابل للتمرير واللمس. |
| `app/src/main/java/com/universestream/app/ui/components/CategoryRow.kt` | padding خاص بـ Compact باستخدام start/end الآمنين لمعالجة القص في RTL دون تغيير TV. |
| `app/src/main/java/com/universestream/app/ui/components/ContinueWatchingRow.kt` | أبعاد هاتفية `156x88dp` لمسار Compact مع إبقاء `208x117dp` لمسار التلفزيون. |
| `app/src/main/java/com/universestream/app/ui/components/shell/AppShell.kt` | safe-area inset وأهداف لمس هاتفية، مع تعطيل mouse support في Compact فقط. |
| `app/src/main/res/values/strings.xml` | موارد نصية لبطاقات Providers Compact. |
| `app/src/main/res/values-ar/strings.xml` | الترجمات العربية المقابلة لواجهة Providers Compact. |

## الأسباب الجذرية والإصلاحات

### 1. Live TV يعرض صفر قنوات أو يبقى في التحميل

كان زر **All** في `MobileLiveTvContent` يمسح بحث الفئة فقط، ولا يختار كائن `ALL_CHANNELS_ID` الذي يبدأ تدفق تحميل القنوات. كذلك كان الاختيار التلقائي للفئة الأولى قد يحدد فئة Recent، وهي فارغة غالباً عند التشغيل الأول. أضيف الآن اختيار صريح لـ All Channels من قائمة الفئات في الزر والحوار، مع `LaunchedEffect` يختارها عند عدم وجود فئة محددة. كما أصبحت حالة تحميل الفئات منفصلة عن حالة تحميل القنوات، وتتحول الشاشة إلى empty state بعد fallback معقول عندما لا توجد مزامنة جارية ولا تصل قنوات حقيقية.

الإصلاح لا يضيف بيانات وهمية ولا يستبدل مصدر المزود؛ القنوات ما زالت تأتي من Room/repository ومسار المزامنة الحالي. نجاح العرض النهائي يظل مرتبطاً بصحة بيانات اعتماد المزود ووصول خادم Xtream.

### 2. زر إظهار كلمة المرور لا يستجيب على الهاتف

كان `mouseClickable` في الحاوية الخارجية يستهلك أحداث اللمس قبل وصولها إلى `clickable` الخاص بأيقونة العين. تم فصل معالجة اللمس في مسار Compact وإيقاف اعتراض mouse support للهاتف فقط، بينما يظل مسار D-pad/TV يستخدم السلوك الافتراضي السابق.

### 3. بيانات اعتماد Xtream لا تُحفظ بعد إعادة التشغيل

لم يكن `ProviderSetupViewModel` يعتمد على `PreferencesRepository` ولم تكن هناك مسودة typed في DataStore. أضيفت مفاتيح server URL وusername وكلمة المرور المشفرة، مع استخدام `CredentialCrypto.encryptIfNeeded` قبل التخزين و`decryptIfNeeded` عند الاستعادة. لا يتم تخزين كلمة المرور plaintext ولا تسجيلها. الحفظ يحدث بعد `Saved` أو `SavedWithWarning` فقط، وليس بعد فشل المصادقة.

### 4. شاشة Providers تظهر كتخطيط تلفزيون على الهاتف

كان `SettingsScreen` يرسم rail وside panel دائماً. أضيف فرع `AppWindowSizeClass.Compact` يحتوي على شريط أقسام قابل للتمرير وقائمة Providers أحادية العمود، بينما بقي مسار TV داخل فرع `else`.

### 5. قص أول وآخر عناصر الرفوف في RTL

كانت بعض الرفوف تعتمد على padding أفقي ثابت غير مناسب لاتجاه القراءة. أضيفت قيم `start/end` لمسار Compact حتى يحصل أول وآخر عنصر على مساحة آمنة في العربية وRTL. لا تُستخدم هذه القيم في مسار التلفزيون.

### 6. Continue Watching كبيرة على الهاتف

كانت البطاقات ثابتة على `208x117dp`. خُصصت أبعاد Compact إلى `156x88dp` لتناسب الهاتف، مع الإبقاء على قياس التلفزيون الأصلي.

### 7. Bottom navigation

أضيف safe-area inset وأصبحت مساحة عناصر اللمس الهاتفية لا تقل عن 52dp تقريباً في مسار Compact. ترتيب العناصر بقي معتمداً على القائمة الحالية وLayoutDirection في Compose، ولم يُفرض ترتيب منفصل على التلفزيون.

## تأكيد عدم تغيير Android TV

لم يتم تعديل `AppWindowSizeClass.Television` أو أبعاده أو typography أو focus/D-pad behavior. تعديلات المكونات المشتركة محمية بفروع Compact: مسار TV يحتفظ بأبعاد الرفوف، قياس Continue Watching، mouse support، padding الافتراضي، وتخطيط Settings rail/side panel. تمت مراجعة diff ساكنة قبل الدفع، كما اجتاز CI بناء release والتحقق من التوقيع.

هذا التأكيد ساكن/بنيوي، وليس بديلاً عن اختبار الجهاز. لا تتوفر في بيئة التنفيذ شاشة Android TV فعلية لإجراء اختبار D-pad بصري كامل.

## نتائج التحقق

| الاختبار | النتيجة |
|---|---|
| `git diff --check` | ناجح قبل commit |
| Hilt binding لـ `CredentialCrypto` | موجود عبر `RepositoryModule` |
| بناء محلي | تعذر قبل compile بسبب عدم توفر Android SDK في sandbox؛ ليس خطأ مصدر مثبتاً |
| CI release compile/build | ناجح |
| توقيع APK | ناجح في CI |
| فحص APK كأرشيف ZIP | ناجح محلياً بعد تنزيل asset الموثق |
| working tree بعد الدفع | نظيف |
| اختبار هاتف فعلي بالعربية portrait | يحتاج تنفيذ المستخدم على الجهاز |
| اختبار هاتف فعلي landscape | يحتاج تنفيذ المستخدم على الجهاز |
| اختبار Android TV فعلي وD-pad | يحتاج جهاز TV فعلي |

## الاختبار الفيزيائي المقترح

على الهاتف العربي، يُرجى تثبيت Build 27 ثم اختبار تسجيل الدخول بمزود Xtream حقيقي، الانتظار حتى ظهور القنوات، الضغط على All وفتح الحوار، تجربة eye icon، إغلاق التطبيق من قائمة recent ثم فتحه للتحقق من استعادة URL وusername وpassword، وتبديل الاتجاه بين portrait وlandscape. بعد ذلك يُختبر RTL في Movies وSeries، ثم Providers وBottom navigation مع الحواف الآمنة.

على Android TV، يجب اختبار تسجيل الدخول، D-pad focus، rail، Settings side panel، Live TV، player، carousels، وContinue Watching. لا توجد في هذا الإصدار تغييرات مقصودة لمسار التلفزيون، لكن التحقق على جهاز حقيقي يبقى خطوة قبول نهائية.

## الحالة النهائية

الإصدار جاهز للتجربة من الرابط المرفق. تم دفع المصدر إلى `main`، وبناء APK الموقّع ونشره كـ **CI Build 27**. المتبقي ليس إصلاحاً معروفاً في المصدر، بل التحقق الفيزيائي على هاتف عربي portrait/landscape وعلى Android TV فعلي، مع اختبار مزود IPTV الحقيقي للتأكد من أن بياناته متاحة وقابلة للمزامنة.
