# Sinfonia Technica – Android-App

Spielt die Musik von [sinfonia.ruthner.at](https://sinfonia.ruthner.at) am Android-Handy, auch bei gesperrtem Bildschirm.

- Alben und einzelne Songs, Radio (alles gemischt, endlos), Favoriten, Einschlaf-Timer
- Wiedergabe als Vordergrunddienst (Media3/ExoPlayer) mit Steuerung über Benachrichtigung, Sperrbildschirm und Bluetooth
- Albumliste kommt live aus `/sinfonia-catalog.json`, neue Alben erscheinen ohne App-Update
- Gehörte Songs bleiben bis 500 MB am Gerät gespeichert

Bauen: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`

Signierung: Für gleichbleibend signierte Builds (Update über die alte Version installieren) `keystore.properties.example` nach `keystore.properties` kopieren und Keystore samt Passwörtern eintragen. Ohne diese Datei wird mit dem Debug-Schlüssel signiert. Nicht für den Play Store gedacht.
