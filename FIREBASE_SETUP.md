# Attention+ — Firebase integráció és üzembe helyezés

## Jelenlegi éles állapot — 2026-11-16

- Projekt: `attentionplus-adef4`; Android-app: `com.dattila.attentionplus`, külön telepítés.
- A korábbi `com.example.a` appot nem töröltük. A forrás névtere továbbra is `com.example.a`.
- Firebase Authentication: Email/Password és Google engedélyezve; API-val visszaellenőrizve.
- A friss `app/google-services.json` tartalmazza az Android és web OAuth-klienst. A debug SHA-1/SHA-256 hozzáadva a Firebase Android-apphoz.
- Firestore `(default)` adatbázis létrehozva `eur3` európai, több régiót használó helyen. Az itt található szabályok és indexek élesítve.
- A felhasználó kérése szerint **Spark, ingyenes csomag maradt**. Nem kapcsoltunk be számlázást.
- **Cloud Functions és Cloud Storage nincs élesítve:** a telepítést a Firebase a Blaze követelménye miatt elutasította. A kód és a helyi integrációs tesztek elkészültek.

Ezért jelenleg az e-mailes belépés, a profil szöveges adatai, a jegyzetek, a munkamenetek, az előzmények, a beállítások és a függő jutalomigények kerülhetnek éles Firestore-ba. A saját képek fájltartalma helyi feltöltési sorban marad; másik eszközön nem tölthető le, amíg nincs Storage. A vásárlás, aktiválás, automatikus szériavédelem és teljes fióktörlés szintén a nem telepített backendtől függ. A felhasználónév-regiszter, a felhasználónévvel történő belépés és a hitelesített jutalomjóváírás a későbbi Functions-telepítésig nem működik élesben. A Google-fiók saját profilképének URL-je Firestore-ban megmarad.

## Teljes tartósadat-leltár

A `src/main` forrásokban ellenőriztük a SharedPreferences-, fájl-, rememberSaveable- és állapotkezelést. A ténylegesen létező módosítható tartós adatok kapcsolatai:

- **Fiók:** Firebase UID, e-mail és jelszókezelés a Firebase Authenticationben; automatikusan visszaállított bejelentkezés. Jelszó nem kerül Firestore-ba, naplóba vagy saját preferences fájlba.
- **Profil:** megjelenő név, e-mail, belépési név, születési dátum, profilkép hivatkozása, létrehozás ideje. Google esetén kezdetben az e-mail a megjelenő név, a kép a Google-profilkép. A Profil fülön a név és saját kép módosítható.
- **Tanulási előkészítés:** kiválasztott tantárgy, választott idő, előkészítési lépés, beillesztett jegyzet, fényképhivatkozások. Új munkamenet indításakor az előkészítő lépés visszaáll a tantárgyválasztásra; a korábbi munkamenet saját másolatot őriz a jegyzetből/képekből.
- **Tanulási munkamenet:** stabil UUID, tantárgy, jegyzetek/képek, kezdési idő, időtartam, határidő, megállított állapot és hátralévő idő, korai befejezés, tanult másodpercek, elemzési határidő, folyamatlépés, kérdésszám, eredeti kérdésszöveg/opciók/helyes válasz/magyarázat, lezárt válaszok, témakör, képhivatkozások és MIME/méret metaadatok, eredmény és lezárás. Az újbóli áttekintés új UUID-t és új időzítőt hoz létre, az eredeti rekord nem változik. Hiányzó képnél nem indul helyettesítő anyaggal.
- **Focus:** beállított idő, UUID, kezdés/vég, visszaszámláló vagy felfelé számláló mód, határidő, futás/befejezés, tanult idő. Újraindításkor a valós időbélyegek alapján folytatódik; lejárt visszaszámlálás bekerül az előzményekbe.
- **Előzmények és statisztikák:** tanulások/Focus-befejezések, pontok, XP, napi sorozat, összes idő, tantárgyankénti idő és pontok, napi grafikon. A szint az XP-ből származik, nem külön felülírható érték.
- **Kihívások:** a napi kihívás megjelenítési állapota tartós preferencia; a teljesítés a szerver által jóváhagyott tanuláshoz kötött.
- **Eszköztár és gazdaság:** két termék, tranzakciós vásárlás, tartós műveletazonosítós újrapróbálás, darabszámok, Potionaktiválás, valós idejű 30 perces bónusz, széria befagyasztók automatikus felhasználása. A `functions/catalog/items.json` az Android assets és a szerver közös árlistája: Potion 300 pont, befagyasztó 200 pont. Csak szerver által jóváhagyott pont költhető el.
- **Beállítások:** Focus-idő, profilszűrő, aktív tanulás, kihíváskártya, hangok, rezgés, csökkentett animáció, értesítések, emlékeztetők és időpontjuk, alapértelmezett tanulási idő, napi cél és automatikus szünetek. A szünet 25 perc után 5 percig tart, ezalatt nem fogy a tanulási idő. Az emlékeztető WorkManagerrel, 15 perces ellenőrzéssel működik; a rendszer késleltetheti. A kapcsoló nem helyettesíti az eszköz értesítési engedélyét.
- **Képek:** saját privát másolat az app fájlterületén, tartós UID-s feltöltési sor, Storage-hivatkozás; nem átmeneti külső `content://` jogosultságra támaszkodunk. Max. 20 MB, JPEG/PNG/WebP/HEIC/HEIF. Sikertelen letöltés nem hagy késznek tekintett részleges képfájlt.
- **Korábbi helyi állapot:** az eredeti `learning_progress.state` változatlan JSON-archívuma, a korábbi befejezések és összes XP megőrzésével; aktív folyamat importja.

A megnyitott párbeszédablak, animációs fázis, billentyűzet, görgetési pozíció és egy még el nem mentett szerkesztőablak átmeneti UI-állapot. Nem felhős üzleti adat. A kamera félbehagyott, még el nem fogadott képét sem tekintjük feltöltött jegyzetnek.

## Firestore és Storage szerkezet

Minden személyes dokumentum gyökere `users/{uid}`:

- `profile/main`: fiókprofil.
- `changes/{uuid}`: változtathatatlan esemény `{id, bucket, entity, clock, patch}`. Bucket: `drafts`, `sessions`, `focus`, `preferences`, `challenges`.
- `completions/{sessionId}`: egyszer beküldött eredményigény; újrapróbáláskor ugyanaz az ID.
- `receipts/{sessionId}`: szerver által jóváhagyott pont, idő, dátum, tantárgy és válaszok.
- `stats/summary`, `subjectStats/{subject}`: szerveroldali összesítések.
- `challenges/daily-{YYYY-MM-DD}`: igazolt napi teljesítés.
- `inventory/{id}`, `unlocks/{id}`: szerveroldali tárgyak/jogosultságok.
- `wallet/main`: összes elköltött pont; elérhető egyenleg = igazolt pontok − költés.
- `operations/{uuid}`: változtathatatlan vásárlási/aktiválási bizonylat, idempotencia.
- `boosts/active`, `boostHistory/{uuid}`: szerveridős XP-bónusz intervallumok.
- `consumptions/freeze-{date}`: egyszeri szériavédelem; a védett nap nem számít teljesített tanulási napnak.
- `stats/summary.protectedDays`, `freezeCheckedThrough`: védett és már elbírált UTC-napok; utólag vásárolt befagyasztó nem javít vissza már elveszített szériát.
- `users/{uid}.deleting`: minimális törlési tiltójelzés; megakadályozza, hogy egy még érvényes régi token újra létrehozza a törölt személyes adatokat.
- `rejectedCompletions/{sessionId}`: elutasítás oka; az eredeti előzmény megmarad.
- `legacy/{sha256}`: eredeti helyi JSON és forrásjelölés.

A gyökérszintű `usernames/{name}` és `loginLimits/{name}` gyűjteményeket csak a backend olvashatja/írhatja. Nem nyilvános e-mail-keresőként működnek. Storage: `users/{uid}/media/{uuid}`, privát autentikált letöltés, nem nyilvános tokenes kép-URL.

## Mentés, szinkronizáció és konfliktusok

Az `AccountRepository` először szinkron helyi commitot készít a UID-hoz kötött outboxba, utána küldi Firestore-ba. Snapshot-listenerek fogadják a másik eszköz változásait; WorkManager hálózat esetén újrapróbálkozik. A helyi cache az offline használatot szolgálja, a fiók új eszközön Firestore-ból áll helyre. Az első belépés/regisztráció internetet igényel.

A módosítások mezőszintű, stabil UUID-s események, Lamport-sorrenddel. Eltérő mezők módosítása nem cseréli le az egész rekordot. Ugyanazon mező egyidejű módosítása determinisztikus sorrendben dől el; az összes verzió megmarad a naplóban, de nincs külön konfliktusválasztó felület. A tanulási folyamat nem léphet vissza korábbi szakaszba, válaszok utólag nem változnak, lezárt Focus nem válik újra futóvá. A szerver egy tranzakcióban írja a receiptet, összesítést és kihívást; ismételt beküldés nem jutalmaz újra.

A képfeltöltés a szöveges és munkamenet-mentések után történik, így a Storage hiánya nem akadályozza azokat. A Beállítások jelzi a helyi függő módosításokat, hálózati hibát vagy a még jóváhagyásra váró tanulásokat. Kijelentkezéskor a listenerek és az aktív repository lezárulnak; másik UID cache-e az új fiók UI-jából nem hozzáférhető. Az Android automatikus app-backup ki van kapcsolva, hogy fiók-cache ne kerüljön másik bejelentkezéshez.

## Migráció és külön alkalmazásazonosító

Az appban meglévő régi preferences állapotát az első bejelentkező UID foglalja le. SHA-256 alapján ugyanaz az archívum nem duplikálódik. Csak sikeres szerver-visszaolvasás és az aktív folyamat tartós importja után jelöli késznek; az eredeti forrást akkor sem törli. Második fiók nem örökli ugyanazt az anonim archívumot.

A régi kliensoldali XP nem bizonyítható hiteles jutalomnak. Privát importált előzményként megmarad és látszik, de nem válik szerver által igazolt, elkölthető jogosultsággá.

Az Android sandbox miatt egy új csomag nem olvashatja automatikusan a régi csomag privát adatait. A csatlakoztatott fejlesztői emulátoron a régi `learning_progress.xml` biztonsági másolata a gitből kizárt `.gradle/legacy-backup/` könyvtárba került, és a tesztek után bájtpontosan ellenőrizve átmásoltuk az új telepítésbe migrációs forrásként. Az eredeti telepítés adata változatlan. A felhős import az első valódi fiók belépésekor történik. Más készülék régi telepítéséhez külön export/import vagy az eredeti csomagban kiadott migráció szükséges; a régi appot addig ne távolítsd el.

## Biztonsági szabályok

Telepítendő fájlok: `firestore.rules`, `storage.rules`. Alapértelmezés szerint minden tiltott; személyes adatot csak a megfelelő Firebase UID érhet el. A profil csak engedélyezett mezőkkel írható, alias/létrehozási idő kliensoldalon nem változtatható. E-mail csak az Auth által igazolt aktuális e-mailre frissíthető. A változásnapló és az eredményigények változtathatatlanok, azonos tartalmú újrapróbálás megengedett. A canonical XP, statisztika, tárgy és feloldás közvetlen kliensírása tiltott.

Az `awardCompletion` ellenőrzi az időadatokat, válaszokat és a támogatott kérdéssort; a pontot maga számítja, nem fogad el kliensoldali XP-értéket. Ez nem bizonyítja, hogy a felhasználó tényleg tanult vagy nem ismerte előre a válaszokat: az offline gyakorlóapp nem csalásbiztos vizsgarendszer.

Nincs Firebase Admin privát kulcs az Android-kliensben. Az Admin SDK kizárólag Cloud Functionsben fut. A web API-kulcs nem Admin-hitelesítő adat; a jelszavakat az Authentication kezeli. A felhasználónévvel belépő HTTPS függvény a jelszót csak a Firebase Auth ellenőrzéséhez továbbítja, nem tárolja és nem naplózza; névenkénti próbálkozáskorláttal működik.

## További kézi beállítások

Az Email/Password és Google szolgáltatót már engedélyezted. A friss konfiguráció bekerült a projektbe. A Google-fiókválasztó teljes, valódi fiókkal történő belépését még felhasználói próbával kell ellenőrizni.

Ha később engedélyezed a Blaze csomagot:

1. Firebase Console → Storage → Get started; válassz megfelelő európai régiót, és használd a projekt szabályait.
2. A projektben `npm ci`, majd `npm --prefix functions ci` (Node 22 a backendhez).
3. `functions/.env.attentionplus-adef4` tartalma: `AUTH_WEB_API_KEY=<a google-services.json current_key értéke>`. A helyi fájl már elkészült és gitből kizárt. Ne használj Admin privát kulcsot.
4. `npx firebase deploy --only firestore:rules,firestore:indexes,storage,functions --project attentionplus-adef4`. A függvények régiója `europe-west1`.
5. App Check → Android app → Play Integrity: a kiadási aláírás SHA-256 tanúsítványát állítsd be; fejlesztéskor a debug App Check tokent a Console debug-token listájába kell regisztrálni. A tokent ne commitold. Az éles callable függvények App Check-et követelnek; a helyi emulátorokban ez a követelmény kikapcsolt.
6. Release előtt add hozzá a tényleges release/Play App Signing SHA-1 és SHA-256 értékeket, és tölts le friss `google-services.json`-t. A release APK jelenleg aláíratlan.
7. Ha a felhasználónévvel belépés `iam.serviceAccounts.signBlob` hibát ad: a futtató szolgáltatásfiók kapjon Service Account Token Creator szerepet a saját aláíró szolgáltatásfiókján. Ne adj ilyen szerepet mobilkliensnek.

Már regisztrált debug SHA-1: `46:37:BD:3B:0C:57:38:86:8F:4E:58:B0:B1:0C:6C:2F:57:B8:92:D7`.

Már regisztrált debug SHA-256: `D9:8E:C5:A5:CC:4A:01:C1:3F:EC:67:E8:DE:33:6A:9D:F5:D9:BB:AD:23:5A:24:2D:71:16:20:C5:07:46:0B:EE`.

## Gazdaság, törlés és képek

A vásárlás, pontlevonás, tárgykiosztás és bizonylat egy Firestore-tranzakció. A kliens a művelet UUID-ját lemezre írja és hiba után ugyanazzal próbálkozik, így egy elveszett válasz nem okoz kettős levonást. Az aktív Potion lejárata szerveridőből számolódik, újraindításkor nem indul újra. Csak a tanulási jutalom XP-je duplázódik, az elkölthető pont nem. Az intervallumba tartozást a befejezésigény szerverhez érkezési ideje dönti el; későn szinkronizált offline befejezés nem kap visszadátumozott bónuszt. A korábbi bónuszintervallumok megmaradnak a késve futó eseménykezelők számára.

A `protectDailyStreaks` naponta 00:10 UTC-kor fut; belépéskor és jutalmazás előtt is történik egyeztetés. A napi széria UTC szerint értelmezett. A Cloud Scheduler tényleges éles futását Spark mellett nem ellenőriztük.

A `deleteAccount` friss újrahitelesítést követel. Először tiltja a további írásokat, majd eltávolítja a UID alatti Storage-fájlokat, Firestore-gyűjteményeket, aliast és Auth-fiókot. A kliens csak siker után törli a saját helyi másolatát. Más felhasználóhoz tartozó régi migrációs forrást nem töröl. A szerveren a törlési tiltójelzés marad meg.

A Storage-szabály a Firestore tiltójelzését is ellenőrzi. Későbbi Storage-telepítéskor a Console/CLI által kért Firestore-hozzáférést engedélyezni kell a Storage Rules szolgáltatásnak: [hivatalos dokumentáció](https://firebase.google.com/docs/storage/security/rules-conditions).

A jelszócsere Firebase Authban történik; e-mail-cserét a kiküldött ellenőrző hivatkozással kell befejezni. Az adatexport a saját profil, munkamenetek, előzmények, preferenciák, gazdasági adatok és függő változások JSON-másolata; jelszót nem tartalmaz. A rendszer fájlválasztójában menthető, hibabejelentéshez a rendszer megosztója használható.

## Ellenőrzések és reprodukálás

- Build: debug és optimalizált, aláíratlan release APK sikeresen elkészült; lint 0 hiba, 66 figyelmeztetés és 1 tájékoztatás; 4/4 helyi egységteszt sikeres. Kotlin/Compose/AGP/Gradle verzió nem változott.
- Backend: `node --test test/firebase/domain.test.js test/firebase/integration.test.js`, futó Firebase-emulátorokkal; 11/11 ellenőrzés sikeres. Tartalmaz UID-elkülönítést, immutable rekordokat, képhozzáférést, profilvalidálást, felhasználónév-belépést, offline visszajátszást, egyszeri szerverjutalmat, atomi/idempotens vásárlást, szerveridős XP-bónuszt, egyszeri szériavédelmet és a törölt fiók régi tokenjének tiltását.
- Egyparancsos helyi backend-futtatás: `npm run test:rules` (ha az emulátorportok szabadok).
- Android: `FirebaseIntegrationTest`, `LearningRedesignTest`, `PageTransitionTest`, API 37 emulátoron: **21 célzott ellenőrzés sikeres** (19 a közös futásban, a javított eszköztár-teszt külön újrafuttatva, plusz a füzetanimáció megszakítási tesztje). A füzet eredeti felhős képe cache-törlés után visszatöltődött, az ismétlés új UUID-t kapott, hiányzó eredeti kép nem indított új tanulást. A vásárlás és Potionaktiválás az app felületéről is átment. A beállítások mentése, automatikus szünet, korai befejezés és navigáció ellenőrizve. Ez célzott regressziós kör, nem a régi, felváltott dizájnokra írt teljes tesztcsomag. A teljes folyamatleállítás utáni újraindítás külön ellenőrzése is sikeres: új belépés nélkül helyreállt a főmenü és a fiók. Firebase-suite csak debug és `developer.firebaseEmulators=true` esetén fut; a produkciós projektet nem használja.
- Windows-emulátorhoz az Auth 9099, Firestore 8080, Storage 9199 és Functions 5001 portokra `adb reverse tcp:PORT tcp:PORT`; a debug `developer.emulatorHost` értéke `127.0.0.1`. Ez a kapcsoló release-ben nem működik. A kiadott debug telepítésnél is ki kell kapcsolni a helyi tesztmódot.
- Éles Spark smoke-teszt: e-mailes regisztráció, hibás/helyes jelszó, profil létrehozása, két külön Firebase-kliens szinkronja, offline írás visszatöltése, másik UID olvasásának és hamis XP írásának tiltása sikeres. Az ideiglenes fiókokat és dokumentumokat a teszt végén eltávolítottuk.
- A többeszközös protokollt két független Firebase-klienssel ellenőriztük; két fizikai Android-telefonos teszt nem történt.
- Google-fiókos teljes belépés, e-mail/jelszócsere teljes UI-folyamata, Android fájlválasztós export, értesítések tényleges időzített kézbesítése, éles Storage, éles Cloud Functions/Scheduler, Play Integrity és aláírt Play-kiadás nem lett végponttól végpontig ellenőrizve. Ezeket nem tekintjük igazolt éles szolgáltatásnak.
- Az animációkat emulátoros állapot- és képi ellenőrzéssel vizsgáltuk, garantált 60 FPS-ről nincs fizikai eszközös mérés.
- Ellenőrzési naplók: `.gradle/ui-final-build.log`, `.gradle/ui-device-final.log`, `.gradle/ui-device-retest.log`, `.gradle/ui-motion-test.log`, `.gradle/shop-backend-tests.log`. A Firestore szabályok/indexek aktuális telepítése: `.gradle/ui-firestore-deploy.log`.

Az app felirata **„AI kép elemzés”**, de a meglévő elemzés továbbra is 26 másodperces helyi folyamat, rögzített történelem-kérdéssorral. Ez a feladat Firebase-adatkezelést adott hozzá, valódi AI-modellt nem kötött be.

## Hivatalos dokumentáció

- [Firebase Android kiadások és BoM](https://firebase.google.com/support/release-notes/android)
- [Google-belépés Androidon](https://firebase.google.com/docs/auth/android/google-signin)
- [Firestore offline működés](https://firebase.google.com/docs/firestore/manage-data/enable-offline)
- [Firestore tranzakciók](https://firebase.google.com/docs/firestore/manage-data/transactions)
- [Cloud Functions üzembe helyezés](https://firebase.google.com/docs/functions/get-started)
- [Cloud Storage kezdő lépések](https://firebase.google.com/docs/storage/android/start)
