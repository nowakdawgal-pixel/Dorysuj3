# Dorysuj! 3.0 — projekt Android od zera

Projekt Kotlin/Android z 130 pozycjami poziomów, menu, ekranem gry, wyborem poziomów, monetami, podpowiedziami, ustawieniem awatara i dźwięku oraz lokalnym zapisem postępu.

## Budowanie APK
1. Otwórz projekt w Android Studio (JDK 17, Android SDK 35).
2. Poczekaj na synchronizację Gradle.
3. Wybierz Build > Build APK(s).
4. APK debug znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`.

Można też użyć workflow GitHub Actions z pliku `.github/workflows/android.yml`.

## Ważne ograniczenia tej wersji
- Ilustracje są rysowane wektorowo w kodzie, a poziomy częściowo korzystają z powtarzanych typów ilustracji. Lista ma 130 wpisów, ale nie jest to 130 całkowicie unikalnych, ręcznie zaprojektowanych zagadek.
- Weryfikacja rysunku sprawdza, czy wykonano odpowiednio długi rysunek; nie ocenia semantycznie, czy narysowano właściwy brakujący element. To prototyp mechaniki, który wymaga dalszego dopracowania.
- Brak podkładu muzycznego i pełnej personalizacji imienia w interfejsie; dźwięk to prosty efekt systemowy.
- Projekt nie został skompilowany ani przetestowany na fizycznym urządzeniu w środowisku jego tworzenia. Przed publikacją trzeba zbudować APK i przeprowadzić testy.
