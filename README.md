# Accueil TV (Bbox 4K)

Launcher Android TV perso : favoris, bouton TV en direct, heure/météo, tuiles Home Assistant,
notifications HA par-dessus l'image, état de la TV remonté dans HA.

## Installation
```
adb install -r app-debug.apk
adb shell appops set fr.gateau.tvhome SYSTEM_ALERT_WINDOW allow
adb shell appops set fr.gateau.tvhome GET_USAGE_STATS allow
adb push config.json /sdcard/Android/data/fr.gateau.tvhome/files/config.json
adb shell am start -n fr.gateau.tvhome/.MainActivity
```
La config est relue à chaque retour sur l'accueil.

## En faire l'accueil par défaut (optionnel)
```
adb shell cmd package set-home-activity fr.gateau.tvhome/.MainActivity
```
Revenir à l'accueil Bouygues :
```
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
adb uninstall fr.gateau.tvhome
```
