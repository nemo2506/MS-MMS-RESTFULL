# 🚀 SMS/MMS Or WITH OVH — Application Android Sender ID Alphanumérique

![Android MVVM](https://img.shields.io/badge/Android-MVVM-green?style=for-the-badge)
![Sécurité EncryptedSharedPreferences](https://img.shields.io/badge/Sécurité-EncryptedSharedPreferences-blue?style=for-the-badge)
![DI Hilt](https://img.shields.io/badge/DI-Hilt-yellow?style=for-the-badge)
![Confidentialité Git](https://img.shields.io/badge/Confidentialité-Git-orange?style=for-the-badge)

---

> 📱 **Application Android moderne** pour l’envoi de SMS via l’API OVH, avec **gestion sécurisée** des identifiants, **architecture MVVM**, **injection de dépendances (Hilt)**, **Sender ID alphanumérique**, **thèmes personnalisés** et **protection de la confidentialité** (Git).

---

## ✨ Fonctionnalités principales

🟢 **Envoi de SMS** via l’API HTTP OVH avec ou sans Sender ID alphanumérique  
🔒 **Authentification sécurisée** (EncryptedSharedPreferences, MasterKey)  
🏗️ **Architecture MVVM** (ViewModel, UseCase, Repository, DataSource)  
🧩 **Injection de dépendances** avec Hilt  
🔔 **Gestion dynamique des permissions** SMS et batterie (Doze)  
🌍 **Support multilingue** (français/anglais)  
🎨 **Thème clair/sombre**, identité graphique personnalisée  
🖼️ **Icônes vectorielles** importées, projet totalement indépendant  
✅ **Bonnes pratiques Android** (modularité, testabilité)  
🛡️ **Confidentialité** (.gitignore & .git/info/exclude)  
📡 **Journalisation automatique** des statuts SMS (succès/échec) via une API locale (`/api/logs`)  
🌐 **Détection dynamique de l’IP locale** pour l’API de logs (NetworkInfoProvider)  
🔄 **Gestion avancée des erreurs SIM/SMS** (retours contextualisés, logs API)  
🧑‍💻 **Icône d’application dynamique** : couleurs du thème appliquées à l’icône (vectorielle, support clair/sombre)  
🌐 **Affichage de l’IP active et des endpoints** dans l’interface principale  
🚫 **Suppression de toute URL d’envoi et de tout affichage de logs/statuts** dans l’interface utilisateur

---

## 🏗️ Architecture MVVM & Journalisation

```mermaid
%%{init: {'flowchart': {'curve': 'linear', 'rankSpacing': 60, 'nodeSpacing': 40}, 'theme': 'dark'}}%%
flowchart LR
    Activity[MainActivity]
    Screen["MainScreen<br/>Compose"]
    ViewModel["MainViewModel<br/>+ BLE"]
    State["MainUiState<br/>StateFlow"]
    UC1["UseCases<br/>SendRest SendOvh<br/>GetSettings"]
    Repository["Repositories<br/>Sms Settings Ble"]
    Room[("Room Database<br/>Settings + Logs")]
    Crypto["Encrypted<br/>SharedPrefs"]
    Manager["ServiceControl<br/>Manager"]
    FG["SmsOvh<br/>ForegroundService"]
    Server["SmsRestServer<br/>api/send api/logs"]
    SMS["SmsManager"]
    OVH["OVH API"]
    BLE["ESP32<br/>BLE"]
    
    Activity -->|onCreate| Screen
    Screen -->|user input| ViewModel
    ViewModel -->|publish| State
    State -->|render| Screen
    
    ViewModel -->|orchestrate| UC1
    UC1 -->|abstract| Repository
    Repository -->|read/write| Room
    Repository -->|secrets| Crypto
    Repository -->|send| SMS
    SMS -->|transport| OVH
    Repository -->|BLE| BLE
    
    ViewModel -->|start/stop| Manager
    Manager -->|control| FG
    FG -->|host| Server
    Server -->|calls| UC1
    Server -->|log| Room
    
    classDef uiLayer fill:#1a472a,color:#fff,stroke:#4d7a5d,stroke-width:2px
    classDef vmLayer fill:#2d5a3d,color:#fff,stroke:#5d8a6d,stroke-width:2px
    classDef useLayer fill:#3d6a4d,color:#fff,stroke:#6d9a7d,stroke-width:2px
    classDef repoLayer fill:#4d7a5d,color:#fff,stroke:#7daa8d,stroke-width:2px
    classDef storageLayer fill:#5d8a6d,color:#fff,stroke:#8dba9d,stroke-width:2px
    classDef serviceLayer fill:#6d9a7d,color:#fff,stroke:#9dcaad,stroke-width:2px
    classDef restLayer fill:#7daa8d,color:#fff,stroke:#addbbd,stroke-width:2px
    classDef extLayer fill:#8dba9d,color:#000,stroke:#bdecd0,stroke-width:2px
    
    class Activity,Screen uiLayer
    class ViewModel,State vmLayer
    class UC1 useLayer
    class Repository repoLayer
    class Room,Crypto storageLayer
    class Manager,FG serviceLayer
    class Server restLayer
    class SMS,OVH,BLE extLayer
```

*Vue paysage optimisée — Architecture MVVM avec flux de données et services*

---


## 🔒 Sécurité & Confidentialité

> 🔒 **Sécurité & Confidentialité**
>
> - 🔑 **Identifiants OVH + token API** stockés localement de manière sécurisée (`EncryptedSharedPreferences` + `MasterKey`)
> - 🛢️ **Séparation des données** : les réglages applicatifs sont persistés via Room, les secrets restent hors base en clair
> - 🛡️ **API locale protégée par Bearer token** avec validation des entrées et réponses d’erreur JSON
> - 📲 **Principe du moindre privilège** : permissions demandées en runtime uniquement quand nécessaire (SMS, localisation, batterie)
> - 🔔 **Disponibilité contrôlée** : foreground service + gestion batterie/optimisation pour limiter les interruptions
> - 🗂️ **Hygiène Git renforcée** : exclusions actives (`.gitignore`, `.git/info/exclude`) pour secrets, fichiers IDE, build, APK et artefacts lourds

---

## 📦 Technologies & Bonnes pratiques

- 🟣 **Kotlin + AndroidX** (app moderne, base maintenable)
- 🎨 **Jetpack Compose** pour l’interface (`MainScreen`, composants UI dédiés)
- 🏗️ **Architecture MVVM** avec `ViewModel` + `UiState` (StateFlow)
- 🧠 **Domain layer** avec **UseCases** et séparation claire des responsabilités
- 🗂️ **Repository pattern** (`data/repository`) + sources locales/techniques
- 🧩 **Hilt (DI)** pour l’injection des dépendances à l’échelle de l’application
- 🛢️ **Room** pour la persistance locale des réglages et logs (avec limites de rétention)
- 🌐 **API REST locale** embarquée (NanoHTTPD) pour l’exécution distante SMS/MMS
- 🔐 **Sécurité locale**: token API + `EncryptedSharedPreferences` (MasterKey)
- 🔔 **Permissions runtime** et gestion batterie (foreground service, optimisation)
- 🌗 **Thème clair/sombre** aligné sur la configuration système
- 🌍 **Internationalisation FR/EN** via ressources `values` / `values-fr` / `values-en`
- 🧪 **Tests unitaires et instrumentés** (service, viewmodel, circuits API/SMS)

---

## 📚 Documentation projet

- Manuel achat / integration MOS + ESP32 + flash Arduino IDE : `docs/MANUEL_ACHAT_INTEGRATION_MOS_ESP32.md`
- Exemples Python pour l'API REST SMS / MMS : `docs/API_PYTHON_SMS_EXAMPLES.md`
- Historique des corrections MVVM : `CHANGELOG_MVVM_FIX.md`

---

## 📁 Structure du projet

```text
MS-OVH-SMS/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/miseservice/smsovh/
│   │   │   │   ├── SmsOvhApp.kt               ← Application Android + bootstrap global
│   │   │   │   ├── data/
│   │   │   │   │   ├── ble/                   ← Integration Bluetooth / GATT / ESP32
│   │   │   │   │   ├── datasource/            ← Sources techniques et acces plateforme
│   │   │   │   │   ├── local/                 ← Room (DB, DAO, Entity)
│   │   │   │   │   ├── remote/                ← API OVH / appels HTTP / acces distant
│   │   │   │   │   └── repository/            ← Implementations Repository
│   │   │   │   ├── di/                        ← Modules Hilt (bind/provide)
│   │   │   │   ├── domain/
│   │   │   │   │   ├── repository/            ← Contrats metier
│   │   │   │   │   └── usecase/               ← Cas d'usage applicatifs
│   │   │   │   ├── model/                     ← Modeles partages
│   │   │   │   ├── service/                   ← Foreground service + serveur REST local
│   │   │   │   ├── ui/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── MainScreen.kt
│   │   │   │   │   ├── components/            ← Sections Compose reutilisables
│   │   │   │   │   └── theme/                 ← Couleurs, typo, themes light/dark
│   │   │   │   ├── util/                      ← Helpers (SMS, token, reseau, permissions)
│   │   │   │   └── viewmodel/                 ← MainViewModel + etat UI
│   │   │   └── res/                           ← Ressources Android (values, mipmap, xml...)
│   │   ├── test/java/com/miseservice/smsovh/
│   │   │   ├── data/                          ← Tests unitaires couche data
│   │   │   ├── service/                       ← Tests unitaires services
│   │   │   ├── util/                          ← Tests utilitaires
│   │   │   └── viewmodel/                     ← Tests ViewModel
│   │   └── androidTest/java/com/miseservice/smsovh/
│   │       ├── ApiCircuitTest.kt              ← Tests instrumentes API locale
│   │       └── LocalSmsCircuitTest.kt         ← Tests instrumentes envoi local
│   └── build.gradle
├── docs/
│   ├── API_PYTHON_SMS_EXAMPLES.md
│   └── MANUEL_ACHAT_INTEGRATION_MOS_ESP32.md
├── screenShots/                               ← Captures d'ecran utilisees dans le README
├── CHANGELOG_MVVM_FIX.md                      ← Historique des corrections MVVM
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## 🚀 Installation & Lancement

```bash
# Cloner le projet
# Ouvrir dans Android Studio
# Sync Gradle puis Run sur un appareil réel ou un émulateur
```

## 🔵 Configuration BLE locale (`local.properties`)

Certaines valeurs BLE utilisees pour la connexion au module ESP32 sont definies localement dans `local.properties`.

> ⚠️ Ne jamais versionner de secrets ou d'identifiants materiels reels dans Git.
> Le fichier `local.properties` doit rester local a la machine de developpement.

### Exemple documente (obfusque)

```ini
# BLE local parameters
ble.deviceName=ESP32_S*****
ble.serviceUuid=6f1c2a90-****-****-****-********d5b1
ble.characteristicUuid=a4d91c2e-****-****-****-********9d73
ble.pin=****
# BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT = 2
ble.writeType=2
```

### Signification des parametres

- `ble.deviceName` : nom Bluetooth annonce par le module ESP32
- `ble.serviceUuid` : UUID du service GATT expose par le module
- `ble.characteristicUuid` : UUID de la caracteristique utilisee pour lire / ecrire
- `ble.pin` : code local de securisation / appairage selon votre implementation
- `ble.writeType` : type d'ecriture GATT (`2` = `WRITE_TYPE_DEFAULT`)

### Exemple attendu cote developpeur

```ini
# BLE local parameters
ble.deviceName=<BLE_DEVICE_NAME>
ble.serviceUuid=<BLE_SERVICE_UUID>
ble.characteristicUuid=<BLE_CHARACTERISTIC_UUID>
ble.pin=<BLE_PIN>
# BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT = 2
ble.writeType=2
```

---

## 📸 Aperçu de l'interface

| Onglet SMS — Envoi SMS local | Onglet OVH — Configuration API OVH |
|---|---|
| ![VUE SMS](screenShots/1.%20VUE%20SMS.jpeg "Envoi SMS local") | ![VUE OVH](screenShots/2.%20VUE%20OVH.jpeg "Configuration OVH") |
| Envoyez des SMS ou MMS directement depuis l'appareil via SmsManager. | Configurez vos identifiants OVH (appKey, secret, consumerKey) et envoyez des SMS via l'API OVH. |

| Onglet API REST — Haut | Onglet Power — Pilotage Bluetooth ESP32 |
|---|---|
| ![VUE API HAUT](screenShots/3.%20VUE%20API%20HAUT.jpeg "API REST - Configuration") | ![VUE POWER](screenShots/5.%20VUE%20POWER.jpeg "Pilotage Bluetooth Power") |
| Configuration du port, affichage de l'IP locale et des endpoints disponibles. | Contrôlez le module MOS/relais via Bluetooth, gérez les seuils batterie Min/Max, et les commandes relais/WiFi. |



## 🌐 API REST locale - Accès & Endpoints

### Authentification

- Toutes les routes REST locales sont protégées par token.
- Header obligatoire: `Authorization: Bearer <TOKEN_API>`
- Le token API est généré/copiable depuis l'interface de l'application.
- Sans token valide, la réponse est: `401 Unauthorized`.

### Base URL

- `http://<IP_DU_TELEPHONE>:<PORT>`
- Exemple: `http://<IP_DU_TELEPHONE>:<PORT>`

### Endpoints disponibles

| Methode | Endpoint | Description | Body JSON |
|---|---|---|---|
| `POST` | `/api/send-message` | Envoi intelligent SMS/MMS selon presence de `base64Jpeg` | `senderId?`, `recipient`, `text`, `base64Jpeg?` |
| `POST` | `/api/send-sms` | Envoi SMS texte | `senderId?`, `recipient`, `text` |
| `POST` | `/api/send-mms` | Envoi MMS avec image | `senderId?`, `recipient`, `text?`, `base64Jpeg` |
| `GET` | `/api/logs` | Retourne les 5 derniers logs persistés | Aucun |
| `POST` | `/api/logs` | Ajoute un log applicatif | `message` |
| `GET` | `/api/battery` | Retourne l'état batterie du téléphone | Aucun |

### Exemples de requêtes

```bash
curl -X POST "http://<IP_DU_TELEPHONE>:<PORT>/api/send-message" -H "Content-Type: application/json" -H "Authorization: Bearer <TOKEN_API>" -d "{\"senderId\":\"MYBRAND\",\"recipient\":\"+33612345678\",\"text\":\"Test REST\",\"base64Jpeg\":\"\"}"
curl -X GET "http://<IP_DU_TELEPHONE>:<PORT>/api/logs" -H "Authorization: Bearer <TOKEN_API>"
curl -X GET "http://<IP_DU_TELEPHONE>:<PORT>/api/battery" -H "Authorization: Bearer <TOKEN_API>"
```

### Formats de reponses JSON

Succes (`200`):

```json
{
  "success": true,
  "message": "SMS envoyé avec succès",
  "timestamp": 1775227009115,
  "type": "SMS"
}
```

Erreur (`400`, `401`, `404`, `500`):

```json
{
  "success": false,
  "error": "Missing request body",
  "code": 400,
  "timestamp": 1775226365345
}
```

Réponse dédiée `/api/battery` (`200`):

```json
{
  "success": true,
  "level": 78,
  "isCharging": false,
  "timestamp": 1775227009115
}
```

### Regles de validation importantes

- `recipient` est obligatoire pour les envois SMS/MMS et est normalisé avant envoi.
- `text` est obligatoire pour `/api/send-message` et `/api/send-sms`.
- `base64Jpeg` est obligatoire pour `/api/send-mms`.
- En cas d'erreur de validation, l'API renvoie un JSON d'erreur (jamais de HTML).
- `/api/battery` renvoie le pattern: `success`, `level`, `isCharging`, `timestamp`.

### Format image `base64Jpeg` (MMS)

- Format attendu: image JPEG encodée en Base64 (chaîne texte).
- Le serveur accepte:
  - Base64 brut: `"/9j/4AAQSkZJRgABAQ..."`
  - Data URI: `"data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."`
- Espaces et retours à la ligne sont nettoyés côté serveur.
- Taille recommandée: <= 3 MB (limite MMS configurée dans l'app).
**Limite stricte MMS en France : 300 à 600 Ko selon l’opérateur (Bouygues, SFR, Orange, Free, etc.).**
  - Si l’image dépasse cette taille, l’envoi échouera ou l’image sera tronquée.
  - Il est recommandé de compresser/redimensionner l’image avant l’envoi.
  - La taille maximale acceptée peut varier selon le pays et l’opérateur, mais ne jamais dépasser 600 Ko pour une compatibilité maximale.

Exemple Python :

Voir la documentation dediee : `docs/API_PYTHON_SMS_EXAMPLES.md`

Exemple PHP:

```php
<?php
$bytes = file_get_contents('image.jpg');
$base64Jpeg = base64_encode($bytes);

$payload = [
    'senderId' => 'MYBRAND',
    'recipient' => '+33612345678',
    'text' => 'MMS test',
    'base64Jpeg' => $base64Jpeg,
];
```

Exemple Android (Kotlin):

```text
import android.util.Base64
import java.io.File

val imageBytes = File("/sdcard/Download/image.jpg").readBytes()
val base64Jpeg = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

val payload = mapOf(
    "senderId" to "MYBRAND",
    "recipient" to "+33612345678",
    "text" to "MMS test",
    "base64Jpeg" to base64Jpeg
)
```

---

## 📡 API OVH utilisée

- Appel HTTP GET à l’API OVH SMS (voir doc officielle)
- Gestion des codes retour (100 = succès, 201/202 = erreur login/mdp, etc.)

---

## ⚠️ Limitations du Sender ID alphanumérique

> ⚠️ **Limitations du Sender ID alphanumérique**
>
> - Max 11 caractères (lettres/chiffres)
> - Pas de réponse possible
> - Doit être validé chez OVH
> - Certains opérateurs/pays peuvent le bloquer

---

## 🤝 Support & Contributions

Pour toute question ou contribution, ouvrez une issue ou une pull request.

---

## 🗂️ Nomenclature des documents

| Document | Emplacement | Rôle |
|---|---|---|
| Manuel MOS + ESP32 | `docs/MANUEL_ACHAT_INTEGRATION_MOS_ESP32.md` | Achat, cablage, flash Arduino IDE, BLE, relais et integration ESP32 |
| Exemples Python API REST | `docs/API_PYTHON_SMS_EXAMPLES.md` | Guide de configuration Python pour consommer l'API REST SMS / MMS |
| Historique MVVM | `CHANGELOG_MVVM_FIX.md` | Trace des corrections et ajustements lies a l'architecture et au ViewModel |

---

> © 2026 MISESERVICE — Architecture MVVM, sécurité, bonnes pratiques Android.
