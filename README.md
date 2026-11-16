# Attention+  Tanulás, fókuszálás és felhős szinkronizáció

Az Attention+ magyar nyelvű, Jetpack Compose-alapú Android-alkalmazás. A bejelentkezési és regisztrációs felület átlátszó logót használ. A főmenü illusztrált kártyái és a diplomás kalap megmaradtak. A **Tanulási napló** vonalas füzetként nyílik ki, önállóan hajló lapokkal, mentett eredményekkel és az eredeti tananyag ismételt áttekintésével.

A **Bolt** két fapolcon hat tárgyhelyet és két terméket jelenít meg. Megnyitásakor oldalirányú átmenet indul, a felfüggesztett tábla pedig a gyorsulásra reagáló fizikai mozgást végez. A **Beállítások** a tanulási időt, napi célt, emlékeztetőket, hangokat, rezgést és csökkentett animációkat kezeli, fiók- és adatkezelési lehetőségekkel. A visszanyilak figyelembe veszik a rendszer sávjait és a tényleges előző oldalt.

## Android-projekt

Az alkalmazás külön telepíthető azonosítója `com.dattila.attentionplus`; a forráskód névtere `com.example.a`. A korábbi `com.example.a` telepítést nem távolítja el. A minimális Android-verzió API 33, a fordítási és célverzió API 37. A Kotlin, Compose, AGP és Gradle verziója nem változott.

## Fiókok és adatmentés

A jelszavakat és a Google-hitelesítést a Firebase Authentication kezeli. A Cloud Firestore tárolja a profilokat, a változtathatatlan módosítási naplót, a tanulási vázlatokat és munkameneteket, a fókuszálási adatokat, beállításokat, korábbi helyi adatok archívumát és szerver által jóváhagyott jutalmakat.

A fiókonként elkülönített, tartós küldési sor és a WorkManager megőrzi az offline módosításokat, majd újrapróbálja a szinkronizálást. A privát jegyzetképek és profilképek a Cloud Storage-ba kerülnek, ha a szolgáltatás elérhető. Az adatstruktúra, biztonsági szabályok, migráció, beállítások és ellenőrzések részletei a [Firebase-útmutatóban](FIREBASE_SETUP.md) találhatók.

### Az éles rendszer jelenlegi korlátai

A projekt az ingyenes **Spark csomagon** marad. Az e-mailes/jelszavas és Google-belépés szolgáltatói engedélyezve vannak, az Android-konfiguráció tartalmazza a Google OAuth-klienseket, a Firestore szabályai és indexei élesítve vannak.

A Cloud Functions és Cloud Storage kódja elkészült, helyi emulátorokon teszteltük, de a jelenlegi csomagon nem telepíthető élesbe. A képek ezért helyileg várnak feltöltésre. A felhasználónévvel történő belépés, szerveres jutalomjóváhagyás, vásárlás, tárgyaktiválás, ütemezett szériavédelem és teljes fióktörlés a szerveroldali rendszer későbbi telepítését igényli. A Firestore ettől függetlenül menti a tanulási előzményeket, jegyzeteket, beállításokat és függő eredményigényeket. A teljes felhős rendszer tehát még nem érhető el élesben.

## Tanulási napló, Potion és elemzés

A füzetkártya neve **Tanulási napló**; külön „Folytatás” felirat nincs rajta. A **Dupla XP Potion** belső azonosítója továbbra is `xp_potion`, hatása 30 percig tart. Az üvegcse grafikáján nincs szöveg. A korábban mentett vásárlások változatlanok.

Az elemzés felirata a mentett tananyagot követi:

- Képnél: **AI kép elemzés**.
- Szövegnél: **AI szöveg elemzés**.
- Mindkettőnél: **AI kép- és szövegelemzés**.

Az elemzés tényleges működése továbbra is a meglévő, 26 másodperces helyi folyamat és tízkérdéses történelem-gyakorlatsor. Valódi AI-szolgáltatás nincs bekötve; jelenleg minden tantárgy ezt a kérdéssort használja.

## Értesítések némítása

Az **Értesítések némítása tanulás közben** mentett kapcsoló a tanulási és fókuszidőzítők alatt az alkalmazás saját Android Ne zavarjanak szabályát vezérli. A hozzáférést a felhasználónak az Android rendszerbeállításaiban kell engedélyeznie.

Az alkalmazás csak a saját szabályát távolítja el; az ébresztők, média- és rendszerhangok engedélyezve maradnak. Egy lejárati rendszeresemény akkor is kezeli az időzítő végét, ha a rendszer közben felszabadítja az alkalmazás folyamatát. Az Android a nem pontos háttérriasztást késleltetheti. A rendszerengedély és a tényleges értesítésnémítás teljes eszközös ellenőrzése még hátravan.

## Fordítás és ellenőrzés

A meglévő Gradle-indítóval és az Android Studio telepített JDK-jával:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug :app:testDebugUnitTest
```

A Firebase-tesztek helyi Authentication-, Firestore-, Storage- és Functions-emulátorokat használnak. Az Android Firebase-tesztcsomagot ne futtasd az éles rendszeren: a fejlesztői emulátorkapcsoló bekapcsolását követeli meg. A parancsokat és az igazolt eredményeket a [Firebase-útmutató](FIREBASE_SETUP.md) részletezi.

Meglévő emulátoros telepítésnél az `adb install -r` és `adb shell am instrument` használható. A Gradle csatlakoztatott eszközös tesztfuttatója a teszt után eltávolíthatja a telepítését. Tesztelés előtt őrizd meg a fejlesztői emulátorbeállításokat és a felhasználói adatok biztonsági másolatát.

A kiadási APK aláíratlan, amíg nincs kiadási kulcs beállítva. A fejlesztői APK közvetlenül telepíthető. A régebbi üveghatású navigáció tesztjei már felváltott terveket írnak le; nem részei a jelenlegi navigáció regressziós tesztkörének.

A legutóbbi felirat- és némítási módosítás fejlesztői fordítása, lintellenőrzése és helyi egységtesztjei sikeresek voltak. Az APK-t telepítettük és elindítottuk az API 37-es emulátoron. A három elemzési feliratot külön egységteszt ellenőrzi. A korábbi Firebase-, bolt- és navigációs tesztek sikere nem jelenti az új némítás teljes rendszeres ellenőrzését.
