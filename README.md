# Fold Files

Dvojpanelový správca súborov pre Samsung Galaxy Z Fold (Kotlin + Jetpack Compose).

## Funkcie
- **Dva panely** – na rozloženom Folde vedľa seba, na vonkajšom displeji pod sebou.
- **Drag & drop** – podrž súbor (~0,5 s) a potiahni ho do druhého panelu alebo na priečinok.
  - Ak je súbor označený, ťahá sa celý výber.
  - Podržanie nad priečinkom ~1 s ho otvorí, potom pusti kamkoľvek do panelu.
  - Prepínač v hornej lište: *Ťahanie: presun* / *Ťahanie: kópia*.
- Označovanie ťuknutím na ikonu, potom kopírovať / presunúť do druhého panelu, premenovať, zmazať.
- Nový priečinok, skryté súbory, otvorenie súboru v inej aplikácii.
- Pri zhode mien sa nič neprepíše – vznikne `meno (1).ext`.

## Zostavenie

**Android Studio:** File → Open → priečinok `FoldFiles` → Run (alebo Build → Build APK).

**GitHub:** každý push na `main` zostaví APK a zverejní ho tu:
https://github.com/freerado-dev/foldfiles/releases/download/latest/FoldFiles.apk

## Prvé spustenie
Aplikácia si vypýta povolenie **Prístup ku všetkým súborom** (Android 11+).
Zapni ho v nastaveniach, ktoré sa otvoria, a vráť sa späť.

Požiadavky: Android 8.0+ (minSdk 26), cieľ Android 15 (SDK 35).
