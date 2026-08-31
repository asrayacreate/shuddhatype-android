# पहिलो crash — के भेटियो, के भेटिएन

## छोटकरीमा

**Stack trace बिना निश्चित कारण भन्न सकिन्न।** मैले अनुमान गरेको छैनँ।
जे-जे जाँच्न सकिन्थ्यो जाँचेँ, दुई निश्चित दोष भेटेर सच्याएँ, तर
"यही कारण थियो" भन्ने प्रमाण मसँग छैन।

एउटा कमाण्डले त्यो प्रमाण दिन्छ — §४ हेर्नुहोस्।

---

## १. तपाईंले शंका गरेका तीन कुरा

| शंका | नतिजा |
|---|---|
| `R.string.*` resolve हुँदैन | **होइन।** `import com.shuddhatype.R` लाइन ४ मा छ, पाँचै reference ठीक |
| Theme मिसिंग | **हो, साँच्चै थिएन** — तर यो अनुमान मात्र, प्रमाण होइन |
| minSdk 24 पुरानो | **होइन।** चलाइएका सबै API २४ भन्दा पुराना; `LinearLayout.getGravity()` (API 24) सबैभन्दा नयाँ |

---

## २. SetupActivity लाई isolate गरेर चलाएँ

तपाईंले भन्नुभएको परीक्षण बनाएँ। Android stub लाई असली runtime जस्तै कडा
बनाएको छु — `addView()` ले parent जाँच्छ, `ScrollView` ले एउटै child मान्छ,
`getString()` ले असली `strings.xml` पढ्छ र नभएको id मा `NotFoundException`
फ्याँक्छ।

```
OK    SetupActivity: instantiate
OK    SetupActivity: onCreate (पूरै view tree बन्छ)
OK    SetupActivity: onCreate + onResume (refreshState)
OK    SetupActivity: onResume दुईपटक (system settings बाट फर्कँदा)
OK    SettingsActivity: onCreate

कुनै crash भएन
```

**अर्थ: SetupActivity को Kotlin logic मा crash छैन।** कारण वातावरणमा छ —
theme, manifest वा dependency।

---

## ३. दुई निश्चित दोष, सच्याइएका

### ३.१ अप्रयुक्त `appcompat` — सबैभन्दा बलियो शंका

`app/src/main/java/` भरि खोज्दा **androidx को एउटै class चलाइएको छैन**।
तर `appcompat` घोषित थियो, र त्यसले यो ल्याउँछ:

```
androidx.appcompat:appcompat:1.7.0
  └── androidx.emoji2:emoji2
        └── androidx.startup:startup-runtime
              └── <provider androidx.startup.InitializationProvider>
```

`InitializationProvider` एउटा ContentProvider हो — **`Application.onCreate()`
भन्दा पहिले चल्छ**। असफल भए एप खुल्नै नपाई मर्छ:

```
java.lang.RuntimeException: Unable to get provider
  androidx.startup.InitializationProvider
```

यो लक्षणसँग मिल्छ: icon थिच्नेबित्तिकै crash, SetupActivity खुल्दै नखुली।
Google सेवा नभएका वा OEM-modified ROM मा `emoji2` को font provider
भेटिँदैन — नेपालमा त्यस्ता फोन धेरै छन्।

**दुवै dependency हटाइयो।** नचलाइएको कुरा जसले तपाईंको कोड सुरु हुनुअघि नै
असफल हुन सक्छ, त्यो राख्नुको कुनै फाइदा छैन।

### ३.२ Theme घोषणा नै थिएन

Manifest मा `android:theme` थिएन, `values/themes.xml` पनि थिएन।

यो crash को कारण थियो कि थिएन पक्का छैन, तर **दोष चाहिँ पक्कै थियो**:
UI गाढा background मा सेतो अक्षर लेख्छ, तर default theme उज्यालो हुन सक्छ —
सेतोमा सेतो।

`Theme.ShuddhaType` थपियो, `@android:style/Theme.DeviceDefault.NoActionBar`
बाट। AppCompat theme जानाजान चलाइएको छैन, किनभने AppCompat अब dependency मै
छैन।

`android:roundIcon` पनि थपियो — PNG पहिल्यै बनेका थिए, manifest मा जोडिएका
थिएनन्।

---

## ४. निश्चित कारण थाहा पाउने तरिका

नयाँ APK चलाउनुअघि **पुरानो build को trace** लिनुहोस् भने सबैभन्दा राम्रो —
मेरो फिक्सले काम गर्‍यो कि गरेन त्यसैले भन्छ:

```bash
adb logcat -c                 # बफर सफा
# अब फोनमा एप icon थिच्नुहोस्, crash हुन दिनुहोस्
adb logcat -d -b crash
```

केही नआए:

```bash
adb logcat -d | grep -A 40 "FATAL EXCEPTION"
```

पहिलो १५ लाइन पठाउनुहोस् — त्यहाँ exception को नाम र ठ्याक्कै लाइन नम्बर
हुन्छ। त्यसपछि अनुमान गर्नुपर्दैन।

**दुई कुरा हेर्नुहोस्:**

- `Unable to get provider androidx.startup.InitializationProvider`
  → ३.१ नै कारण थियो, अब सच्चियो
- `Resources$NotFoundException` वा `InflateException`
  → theme/resource समस्या, ३.२ ले समाधान गर्नुपर्छ
- अरू केही → मलाई पठाउनुहोस्, त्यही हेरेर सच्याउँछु

---

## ५. नयाँ APK

```bash
cd shuddhatype-android
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`clean` छुटाउनु हुँदैन — dependency हटेको छ, पुरानो build cache ले
पुरानै classpath चलाउन सक्छ।

---

## ६. यो build मा फेरि जाँचिएको

| जाँच | नतिजा |
|---|---|
| SetupActivity/SettingsActivity lifecycle (कडा stub) | crash छैन |
| Engine vs JS, ३,७३४ input | ३,७३४/३,७३४ उही |
| आठ golden test | आठै पास |
| `@style`, `@color`, `@mipmap` सबै resolve | पास |
| Theme parent platform style हो (AppCompat नचाहिने) | पास |
| Manifest class, package/path, asset | पास |

**अझै असली Gradle build चलेको छैन** — यहाँ Android SDK र network पहुँच छैन।
Resource merge र APK packaging फोनमै जाँचिन्छ।
