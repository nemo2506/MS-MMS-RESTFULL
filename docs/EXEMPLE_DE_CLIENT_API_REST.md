# Exemple de Client - API REST locale SMS / MMS

## 1) Objectif
Ce document decrit des exemples clients pour piloter l'API REST locale de l'application Android.

Langages couverts :
- Python
- PHP
- Android (Kotlin)

Usages cibles :
- envoi SMS
- envoi MMS
- lecture des logs
- lecture de l'etat batterie

---

## 2) Prerequis

- Python 3.10+ et `requests` (optionnel selon client)
- PHP 8+ avec extension cURL (optionnel selon client)
- Android Kotlin avec client HTTP (optionnel selon client)
- telephone Android et machine cliente sur le meme reseau local
- foreground service actif dans l'application Android
- token API disponible depuis l'interface de l'application

Installation de `requests` :

```powershell
py -m pip install requests
```

---

## 3) Configuration minimale (commune)

Tout client doit en pratique definir :

- `BASE_URL` : URL locale du telephone, par exemple `http://192.168.1.50:8080`
- `API_TOKEN` : Bearer token affiche dans l'application
- `headers` : dictionnaire HTTP avec `Authorization` et, pour les POST JSON, `Content-Type: application/json`

Exemple de valeurs a renseigner :

| Variable | Valeur attendue |
|---|---|
| `BASE_URL` | `http://<IP_DU_TELEPHONE>:<PORT>` |
| `API_TOKEN` | token API local |
| `Authorization` | `Bearer <TOKEN_API>` |

---

## 4) Envoi d'un SMS (principe)

Pour envoyer un SMS depuis n'importe quel client :

1. utiliser un client HTTP
2. construire l'URL `BASE_URL + /api/send-sms`
3. envoyer un `POST` JSON avec :
   - `recipient`
   - `text`
   - `senderId` optionnel
4. transmettre le header `Authorization: Bearer <TOKEN_API>`

Payload attendu :

```json
{
  "senderId": "MYBRAND",
  "recipient": "+33612345678",
  "text": "Test SMS depuis Python"
}
```

---

## 5) Envoi intelligent SMS / MMS

La route `/api/send-message` choisit le mode SMS ou MMS selon la presence du champ `base64Jpeg`.

Payload type :

```json
{
  "senderId": "MYBRAND",
  "recipient": "+33612345678",
  "text": "Message intelligent depuis Python",
  "base64Jpeg": ""
}
```

Si `base64Jpeg` est vide, le comportement attendu est un envoi texte simple.

---

## 6) Envoi MMS avec image

Pour un MMS :

1. ouvrir l'image JPEG en binaire
2. convertir le contenu en Base64
3. renseigner `base64Jpeg` dans le payload JSON
4. envoyer un `POST` vers `/api/send-message` ou `/api/send-mms` selon votre workflow

Champs utiles :

- `recipient`
- `text` optionnel selon la route utilisee
- `base64Jpeg`
- `senderId` optionnel

Conseil : compresser l'image avant conversion Base64 si le poids est eleve.

---

## 7) Lecture des logs

Pour consulter les logs applicatifs depuis Python :

- methode : `GET`
- endpoint : `/api/logs`
- header obligatoire : `Authorization: Bearer <TOKEN_API>`

Le serveur retourne les derniers evenements persistés.

---

## 8) Lecture batterie

Pour lire l'etat batterie du telephone :

- methode : `GET`
- endpoint : `/api/battery`
- header obligatoire : `Authorization: Bearer <TOKEN_API>`

Reponse type :

```json
{
  "success": true,
  "level": 78,
  "isCharging": false,
  "timestamp": 1775227009115
}
```

---

## 9) Structure conseillee d'un client

La structure conseillee d'un script ou client est la suivante :

- initialiser `BASE_URL`
- initialiser `API_TOKEN`
- construire une fonction d'envoi `POST`
- construire une fonction de lecture `GET`
- centraliser les headers HTTP reutilisables
- ajouter un timeout reseau explicite sur chaque requete

Bonnes pratiques :

- utiliser un timeout explicite sur chaque requete
- journaliser le `status_code` et le corps de reponse
- gerer les erreurs reseau avec `try/except`

---

## 10) Exemples clients complets

### 10.1 Python (`requests`)

```python
import requests

BASE_URL = "http://<IP_DU_TELEPHONE>:<PORT>"
TOKEN = "<TOKEN_API>"

headers = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json"
}

payload = {
    "senderId": "MYBRAND",
    "recipient": "+33612345678",
    "text": "Test SMS Python"
}

response = requests.post(f"{BASE_URL}/api/send-sms", json=payload, headers=headers, timeout=30)
print(response.status_code)
print(response.text)
```

### 10.2 PHP (`cURL`)

```php
<?php
$baseUrl = "http://<IP_DU_TELEPHONE>:<PORT>";
$token = "<TOKEN_API>";

$payload = [
    "senderId" => "MYBRAND",
    "recipient" => "+33612345678",
    "text" => "Test SMS PHP"
];

$ch = curl_init($baseUrl . "/api/send-sms");
curl_setopt_array($ch, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_POST => true,
    CURLOPT_HTTPHEADER => [
        "Content-Type: application/json",
        "Authorization: Bearer " . $token
    ],
    CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_UNICODE),
    CURLOPT_TIMEOUT => 30,
]);

$body = curl_exec($ch);
$status = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

echo $status . PHP_EOL;
echo $body . PHP_EOL;
```

### 10.3 Android (Kotlin + OkHttp)

```kotlin
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

val baseUrl = "http://<IP_DU_TELEPHONE>:<PORT>"
val token = "<TOKEN_API>"

val json = """
    {
      "senderId": "MYBRAND",
      "recipient": "+33612345678",
      "text": "Test SMS Android"
    }
""".trimIndent()

val client = OkHttpClient()
val request = Request.Builder()
    .url("$baseUrl/api/send-sms")
    .addHeader("Authorization", "Bearer $token")
    .post(json.toRequestBody("application/json".toMediaType()))
    .build()

client.newCall(request).execute().use { response ->
    println(response.code)
    println(response.body?.string().orEmpty())
}
```

### 10.4 MMS (base64) - exemple multi-clients

Champ obligatoire : `base64Jpeg`.

```json
{
  "senderId": "MYBRAND",
  "recipient": "+33612345678",
  "text": "MMS test",
  "base64Jpeg": "<BASE64_JPEG>"
}
```

Route conseillee : `POST /api/send-message`.

---

## 11) Reponses attendues

Succes possible :

```json
{
  "success": true,
  "message": "SMS envoyé avec succès",
  "timestamp": 1775227009115,
  "type": "SMS"
}
```

Erreur possible :

```json
{
  "success": false,
  "error": "Missing request body",
  "code": 400,
  "timestamp": 1775226365345
}
```

---

## 12) Conseils de securite

- ne jamais publier le vrai Bearer token dans un depot Git
- eviter d'ecrire le token en dur dans les scripts partages
- preferer des variables d'environnement ou un fichier local non versionne
- limiter l'exposition reseau du telephone hebergeant l'API

Exemple de variable d'environnement sous PowerShell :

```powershell
$env:SMS_API_TOKEN="YOUR_BEARER_TOKEN"
py .\script_sms.py
```

---

## 13) Checklist de validation

Avant de depanner un script Python, verifier que :

- le telephone Android est allume
- l'application est lancee
- le service local est demarre
- l'adresse IP affichee dans l'application est joignable
- le port configure est correct
- le Bearer token est valide
- le reseau local autorise bien la communication entre les appareils

