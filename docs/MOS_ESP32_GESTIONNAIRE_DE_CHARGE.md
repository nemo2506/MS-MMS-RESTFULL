# Integration Telephone Android + MOS ESP32 + Gestionnaire de Charge

## Introduction

Maintenir un telephone en charge permanente a 100% (ou le laisser se vider tres bas trop souvent)
accelere le vieillissement de la batterie lithium.

L'objectif de cette integration est de conserver une **fenetre de fonctionnement saine** :

- demarrer la charge quand le niveau devient trop bas (seuil `MIN`),
- arreter la charge quand le niveau devient trop haut (seuil `MAX`).

Ce pilotage automatique charge/decharge aide a :

- limiter le stress electrochimique,
- reduire l'echauffement prolonge,
- stabiliser les performances dans la duree.

Dans ce projet, le telephone decide la logique (seuils batterie) et le module MOS ESP32 execute
la commutation physique du gestionnaire de charge via son API REST.

Ce document decrit l'integration entre :

- le telephone Android (application `MS-OVH-SMS`),
- le module MOS ESP32 expose en REST,
- le gestionnaire de charge pilote via relais.

## Vue d'ensemble

- Le telephone lit son niveau de batterie local.
- Toutes les 60 secondes, il interroge l'ESP32 (`/api/status/[PIN]`).
- Selon les seuils Min/Max, il bascule la sortie (`/api/power/[PIN]`).
- Le MOS ESP32 commute la ligne de commande du gestionnaire de charge.

## Captures (screenShots)

| Interface Android                                              | ARDUINO IDE                                                          |
|----------------------------------------------------------------|----------------------------------------------------------------------|
| ![VUE POWER](<../screenShots/5. VUE POWER.png> "Onglet Power") | ![ARDUINO IDE](../screenShots/6.%20ARDUINO%20IDE_1.png "Code ESP32") |

## Materiel achete sur AliExpress

Cette integration s'appuie sur deux appareils physiques :

- un adaptateur **USB-TTL** pour le flash et le debug serie,
- un module **MOS-ESP32-UART** pour le pilotage relais du gestionnaire de charge.

| USB-TTL                                                               | MOS-ESP32-UART                                                               |
|-----------------------------------------------------------------------|------------------------------------------------------------------------------|
| ![USB-TTL](../screenShots/USB-TTL.png "Adaptateur USB vers UART TTL") | ![MOS-ESP32-UART](../screenShots/MOS-ESP32-UART.png "Module MOS ESP32 UART") |

### 1) Adaptateur USB-TTL

**Role principal**

- Programmer l'ESP32 depuis l'IDE Arduino.
- Lire les logs serie (`115200`) pendant le demarrage WPS et les appels API.

**Usage dans ce projet**

- Chargement du firmware REST (`/api/power/[PIN]`, `/api/status/[PIN]`).
- Diagnostic rapide en cas d'echec WPS, mDNS ou token Bearer invalide.

### 2) Module MOS-ESP32-UART

**Role principal**

- Exposer l'API REST locale via Wi-Fi.
- Commander la sortie MOS/relais qui active ou coupe le gestionnaire de charge.

**Usage dans ce projet**

- `POST /api/status/[PIN]` : lecture et supervision de l'etat de sortie.
- `POST /api/power/[PIN]` : bascule (toggle) de l'etat pour la charge.
- Integration mDNS : acces via `http://POWER_SWITCH.local`.

**Recommandations de cablage (principe)**

- Sortie GPIO autorisee de l'ESP32 -> entree commande du module MOS/relais.
- Alimentation ESP32 stable (USB/5V selon carte), masse commune avec la partie commande.
- Ligne de puissance du chargeur separee de la logique TTL (isolement conseille selon montage).

### Sequence de mise en service (materiel)

1. Connecter le **USB-TTL** et flasher le firmware ESP32.
2. Ouvrir le moniteur serie et verifier IP + mDNS (`POWER_SWITCH.local`).
3. Brancher le **MOS-ESP32-UART** sur la ligne de commande du gestionnaire de charge.
4. Configurer l'app Android (`URL`, `PIN`, `token`, seuils `MIN/MAX`).
5. Verifier les reponses API `STATUS` puis `POWER`.

## Commandes Firmware ESP32 (REST API)

Le firmware expose des commandes HTTP en **POST** avec authentification Bearer.

### Authentification

- Header obligatoire : `Authorization: Bearer <TOKEN>`
- Sans ce header (ou token invalide), le firmware repond `401 Unauthorized`.

Exemple de header attendu :

```text
Authorization: Bearer 6ec3a985e5084bd0889e77c6cd1f81de
```

### 1) Lire l'etat d'une PIN

- Methode : `POST`
- Endpoint : `/api/status/[PIN]`
- Effet : retourne l'etat courant (`LOW/HIGH`) et la valeur (`0/1`)

Exemple :

```bash
curl -X POST "http://POWER_SWITCH.local/api/status/4" \
  -H "Authorization: Bearer 6ec3a985e5084bd0889e77c6cd1f81de"
```

Reponse type :

```json
{
  "pin": 4,
  "state": "LOW",
  "value": 0
}
```

### 2) Basculer (toggle) une PIN

- Methode : `POST`
- Endpoint : `/api/power/[PIN]`
- Effet : inverse l'etat de la sortie (`LOW -> HIGH` ou `HIGH -> LOW`)

Exemple :

```bash
curl -X POST "http://POWER_SWITCH.local/api/power/4" \
  -H "Authorization: Bearer 6ec3a985e5084bd0889e77c6cd1f81de"
```

Reponse type :

```json
{
  "pin": 4,
  "state": "HIGH",
  "value": 1,
  "action": "toggled"
}
```

### 3) Informations serveur

- Methode : `POST`
- Endpoint : `/`
- Effet : retourne les metadonnees device, IP, hote mDNS et endpoints exposes

Exemple :

```bash
curl -X POST "http://POWER_SWITCH.local/"
```

### Codes de retour utiles

- `200` : commande executee
- `400` : format endpoint invalide (ex: PIN manquante)
- `401` : token absent/invalide
- `403` : PIN non autorisee
- `404` : endpoint inconnu
- `405` : methode non autorisee (autre que POST)

## Firmware ESP32 (REST API)

```cpp
/*
 * ESP32 REST API - GPIO Control via HTTP
 * =======================================
 * Connexion WiFi via WPS au démarrage
 * Auth : Bearer Token (header Authorization: Bearer <TOKEN>)
 * Endpoints :
 *   POST /api/power/[PIN]    → toggle la PIN (OUTPUT)
 *   POST /api/status/[PIN]   → retourne l'état de la PIN
 *
 * Board : ESP32 Dev Module
 * Arduino core ESP32 : >= 2.x (Espressif)
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <esp_wps.h>

// ─── Bearer Token ─────────────────────────────────────────────────────────────
#define BEARER_TOKEN  "6ec3a985e5084bd0889e77c6cd1f81de"

// ─── Hostname mDNS ────────────────────────────────────────────────────────────
// Accessible via http://POWER_SWITCH.local
#define HOSTNAME      "POWER_SWITCH"

// ─── Configuration ────────────────────────────────────────────────────────────
#define WPS_TIMEOUT_MS   60000

const int ALLOWED_PINS[] = {4, 5, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 23, 25, 26, 27, 32, 33};
const int ALLOWED_PINS_COUNT = sizeof(ALLOWED_PINS) / sizeof(ALLOWED_PINS[0]);

// ─── Globals ──────────────────────────────────────────────────────────────────
WebServer server(80);
static volatile bool wpsSuccess  = false;
static volatile bool wpsFinished = false;

// ─── WPS ──────────────────────────────────────────────────────────────────────
static esp_wps_config_t wpsConfig;

void wpsInitConfig() {
  memset(&wpsConfig, 0, sizeof(wpsConfig));
  wpsConfig.wps_type = WPS_TYPE_PBC;
  strcpy(wpsConfig.factory_info.manufacturer, "ESP32");
  strcpy(wpsConfig.factory_info.model_number, "ESP32");
  strcpy(wpsConfig.factory_info.model_name,   "Espressif");
  strcpy(wpsConfig.factory_info.device_name,  HOSTNAME);
}

void WiFiEvent(WiFiEvent_t event, arduino_event_info_t info) {
  switch (event) {
    case ARDUINO_EVENT_WIFI_STA_START:
      Serial.println("[WiFi] Station démarrée");
      break;
    case ARDUINO_EVENT_WPS_ER_SUCCESS:
      Serial.println("[WPS] Succès — connexion en cours...");
      esp_wifi_wps_disable();
      delay(10);
      WiFi.begin();
      break;
    case ARDUINO_EVENT_WPS_ER_FAILED:
      Serial.println("[WPS] Échec");
      esp_wifi_wps_disable();
      wpsFinished = true; wpsSuccess = false;
      break;
    case ARDUINO_EVENT_WPS_ER_TIMEOUT:
      Serial.println("[WPS] Timeout");
      esp_wifi_wps_disable();
      wpsFinished = true; wpsSuccess = false;
      break;
    case ARDUINO_EVENT_WIFI_STA_GOT_IP:
      Serial.print("[WiFi] IP obtenue : ");
      Serial.println(WiFi.localIP());
      wpsFinished = true; wpsSuccess = true;
      break;
    case ARDUINO_EVENT_WIFI_STA_DISCONNECTED:
      Serial.println("[WiFi] Déconnecté");
      break;
    default:
      break;
  }
}

bool connectWPS() {
  Serial.println("\n[WPS] Démarrage WPS PBC...");
  Serial.println("[WPS] Appuyez sur le bouton WPS de votre routeur !");
  WiFi.onEvent(WiFiEvent);
  WiFi.setHostname(HOSTNAME);   // doit être avant WiFi.mode()
  WiFi.mode(WIFI_STA);
  wpsInitConfig();
  esp_wifi_wps_enable(&wpsConfig);
  esp_wifi_wps_start(0);
  unsigned long start = millis();
  while (!wpsFinished) {
    if (millis() - start > WPS_TIMEOUT_MS) {
      Serial.println("\n[WPS] Timeout global atteint");
      esp_wifi_wps_disable();
      return false;
    }
    delay(100);
    Serial.print(".");
  }
  Serial.println();
  return wpsSuccess;
}

// ─── Auth Bearer ──────────────────────────────────────────────────────────────
bool checkBearer() {
  if (!server.hasHeader("Authorization")) return false;
  String authHeader = server.header("Authorization");
  String expected   = "Bearer " + String(BEARER_TOKEN);
  return authHeader.equals(expected);
}

// ─── Helpers REST ─────────────────────────────────────────────────────────────
bool isPinAllowed(int pin) {
  for (int i = 0; i < ALLOWED_PINS_COUNT; i++) {
    if (ALLOWED_PINS[i] == pin) return true;
  }
  return false;
}

void sendJSON(int code, String json) {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(code, "application/json", json);
}

int parsePinFromURI(String uri) {
  int lastSlash = uri.lastIndexOf('/');
  if (lastSlash == -1) return -1;
  String pinStr = uri.substring(lastSlash + 1);
  if (pinStr.length() == 0) return -1;
  for (char c : pinStr) {
    if (!isDigit(c)) return -1;
  }
  return pinStr.toInt();
}

// ─── Handlers ─────────────────────────────────────────────────────────────────

// POST /api/power/[PIN] → toggle OUTPUT
void handlePower() {
  if (!checkBearer()) {
    sendJSON(401, "{\"error\":\"Unauthorized — Bearer token invalide ou manquant\"}");
    Serial.println("[AUTH]    Refus — token invalide");
    return;
  }
  int pin = parsePinFromURI(server.uri());
  if (pin == -1) {
    sendJSON(400, "{\"error\":\"Format : /api/power/[PIN]\"}");
    return;
  }
  if (!isPinAllowed(pin)) {
    sendJSON(403, "{\"error\":\"PIN non autorisee\"}");
    return;
  }
  pinMode(pin, OUTPUT);
  int newState = (digitalRead(pin) == HIGH) ? LOW : HIGH;
  digitalWrite(pin, newState);
  String stateStr = (newState == HIGH) ? "HIGH" : "LOW";
  sendJSON(200,
    "{\"pin\":"    + String(pin) +
    ",\"state\":\"" + stateStr + "\"" +
    ",\"value\":"  + String(newState) +
    ",\"action\":\"toggled\"}"
  );
  Serial.printf("[POWER]   PIN %d -> %s\n", pin, stateStr.c_str());
}

// POST /api/status/[PIN] → lecture état
void handleStatus() {
  if (!checkBearer()) {
    sendJSON(401, "{\"error\":\"Unauthorized — Bearer token invalide ou manquant\"}");
    Serial.println("[AUTH]    Refus — token invalide");
    return;
  }
  int pin = parsePinFromURI(server.uri());
  if (pin == -1) {
    sendJSON(400, "{\"error\":\"Format : /api/status/[PIN]\"}");
    return;
  }
  if (!isPinAllowed(pin)) {
    sendJSON(403, "{\"error\":\"PIN non autorisee\"}");
    return;
  }
  int val = digitalRead(pin);
  String stateStr = (val == HIGH) ? "HIGH" : "LOW";
  sendJSON(200,
    "{\"pin\":"    + String(pin) +
    ",\"state\":\"" + stateStr + "\"" +
    ",\"value\":"  + String(val) + "}"
  );
  Serial.printf("[STATUS]  PIN %d = %s\n", pin, stateStr.c_str());
}

// POST / → info (non protégé)
void handleRoot() {
  sendJSON(200,
    "{\"device\":\"" + String(HOSTNAME) + "\""
    ",\"ip\":\""     + WiFi.localIP().toString() + "\""
    ",\"host\":\""   + String(HOSTNAME) + ".local\""
    ",\"auth\":\"Bearer token requis\""
    ",\"endpoints\":[\"/api/power/[PIN]\",\"/api/status/[PIN]\"]}"
  );
}

// ─── Setup ────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(500);

  // ── Bannière Monitor ────────────────────────────────────────────────────────
  Serial.println();
  Serial.println("╔══════════════════════════════════════════════════════════════╗");
  Serial.println("║               POWER_SWITCH  REST  API                       ║");
  Serial.println("╠══════════════════════════════════════════════════════════════╣");
  Serial.println("║  Auth : Bearer Token                                         ║");
  Serial.println("╠══════════════════════════════════════════════════════════════╣");
  Serial.printf( "║  TOKEN  : %-51s ║\n", BEARER_TOKEN);
  Serial.println("╠══════════════════════════════════════════════════════════════╣");
  Serial.println("║  Header : Authorization: Bearer <TOKEN>                      ║");
  Serial.println("╚══════════════════════════════════════════════════════════════╝");
  Serial.println();

  pinMode(2, OUTPUT);
  digitalWrite(2, LOW);

  if (!connectWPS()) {
    Serial.println("[ERREUR] WPS echoue — redemarrez et reessayez.");
    while (true) {
      digitalWrite(2, HIGH); delay(150);
      digitalWrite(2, LOW);  delay(150);
    }
  }

  digitalWrite(2, HIGH);  // LED fixe = connecté

  // mDNS → http://POWER_SWITCH.local
  if (MDNS.begin(HOSTNAME)) {
    MDNS.addService("http", "tcp", 80);
    Serial.printf("[mDNS]  http://%s.local\n", HOSTNAME);
  } else {
    Serial.println("[mDNS]  Echec démarrage mDNS");
  }

  // Headers à collecter (obligatoire sur ESP32 WebServer)
  const char* headerKeys[] = {"Authorization"};
  server.collectHeaders(headerKeys, 1);

  server.on("/", HTTP_POST, handleRoot);
  server.onNotFound([]() {
    String uri = server.uri();
    if (server.method() != HTTP_POST) {
      sendJSON(405, "{\"error\":\"Methode non autorisee — utilisez POST\"}");
      return;
    }
    if      (uri.startsWith("/api/power/"))  handlePower();
    else if (uri.startsWith("/api/status/")) handleStatus();
    else sendJSON(404, "{\"error\":\"Endpoint non trouve\"}");
  });

  server.begin();

  // ── Résumé après connexion ──────────────────────────────────────────────────
  Serial.println();
  Serial.println("╔════════════════════════════════════════════════════════════════════════════╗");
  Serial.println("║                          SERVEUR DÉMARRÉ                                   ║");
  Serial.println("╠════════════════════════════════════════════════════════════════════════════╣");
  Serial.printf( "║  Device   : %-62s ║\n", HOSTNAME);
  Serial.printf( "║  IP       : %-62s ║\n", WiFi.localIP().toString().c_str());
  Serial.printf( "║  mDNS     : %-62s ║\n", (String(HOSTNAME) + ".local").c_str());
  Serial.println("╠════════════════════════════════════════════════════════════════════════════╣");
  Serial.println("║  ENDPOINTS (POST)                                                          ║");
  Serial.println("║  /api/power/[PIN]                                                          ║");
  Serial.println("║  /api/status/[PIN]                                                         ║");
  Serial.println("╠════════════════════════════════════════════════════════════════════════════╣");
  Serial.println("║  EXEMPLE curl :                                                            ║");
  Serial.printf( "║  curl -X POST -H \"Authorization: Bearer %s\" \\ ║\n", BEARER_TOKEN);
  Serial.printf( "║    http://%s.local/api/power/4               ║\n", HOSTNAME);
  Serial.println("╚════════════════════════════════════════════════════════════════════════════╝");
  Serial.println();
}

// ─── Loop ─────────────────────────────────────────────────────────────────────
void loop() {
  server.handleClient();
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Connexion perdue, reconnexion...");
    WiFi.reconnect();
    delay(5000);
  }
}
```

