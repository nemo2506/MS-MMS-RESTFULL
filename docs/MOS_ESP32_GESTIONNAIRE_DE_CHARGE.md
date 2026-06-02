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

## Plan de déploiement (Workflow)

```mermaid
flowchart TD
    A["🔌 ÉTAPE 1 : DÉMARRER MOS ESP32<br/>Brancher USB ou alimentation 5V"] --> B["📡 ÉTAPE 2 : CONFIGURER WIFI (AP MODE)<br/>Se connecter au réseau 'ESP32-CONFIG'<br/>Navigateur -> 192.168.4.1"]
    B --> C["⏳ ÉTAPE 3 : Saisir Identifiants WiFi<br/>L'ESP32 redémarre et se connecte"]
    C --> D["✅ ÉTAPE 4 : Vérifier Connexion WiFi<br/>LED GPIO23 Fixe • IP locale OK"]
    D --> E{ÉTAPE 5 : Monitoring<br/>avant lancement app ?}
    E -->|OUI| F["🖥️ OPTIONNEL : MONITORER avec IDE ARDUINO<br/>Ctrl+Maj+M • 115200 baud<br/>Vérifier logs AP/STA • IP • mDNS"]
    E -->|NON| G["📱 ÉTAPE 6 : LANCER APPLICATION Android<br/>MS-OVH-SMS • Onglet Power"]
    F --> G
    G --> H["⚙️ ÉTAPE 7 : Configurer Paramètres<br/>URL : http://POWER_SWITCH<br/>Port • PIN • Token • Min/Max"]
    H --> I["🧪 ÉTAPE 8 : Tester Connexion API<br/>Cliquer STATUS • puis POWER<br/>Vérifier réponses JSON"]
    I --> J["▶️ ÉTAPE 9 : Lancer l'AUTOMATION<br/>Cycle 60 secondes démarré<br/>Logs sur console USB-TTL"]
    J --> K["🔋 SUCCÈS ✨<br/>Charge/Décharge gérée automatiquement<br/>Batterie en santé optimale"]
    
    style A fill:#ff6666,stroke:#333,stroke-width:2px,color:#000
    style B fill:#ffaa44,stroke:#333,stroke-width:2px,color:#000
    style D fill:#66bb6a,stroke:#333,stroke-width:2px,color:#000
    style F fill:#64b5f6,stroke:#333,stroke-width:2px,color:#000
    style G fill:#ba68c8,stroke:#333,stroke-width:2px,color:#000
    style J fill:#66bb6a,stroke:#333,stroke-width:2px,color:#000
    style K fill:#4caf50,stroke:#333,stroke-width:3px,color:#fff
```

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
- Lire les logs serie (`115200`) pendant le demarrage WiFi et les appels API.

**Usage dans ce projet**

- Chargement du firmware REST (`/api/power/[PIN]`, `/api/status/[PIN]`).
- Diagnostic rapide en cas d'echec de connexion, mDNS ou token Bearer invalide.

**Schéma de branchement**

![BRANCHEMENT USB-TTL](../screenShots/BRANCHEMENT-USB-TTL.jpg "Connexions USB-TTL vers ESP32")

| Connecteur USB-TTL | → | Port ESP32 | Description |
|---|---|---|---|
| **RX** (entrée) | → | **TX** (GPIO1) | Données reçues par l'adaptateur |
| **TX** (sortie) | → | **RX** (GPIO3) | Données transmises par l'adaptateur |
| **GND** (masse) | → | **GND** | Masse commune (impératif) |
| **3.3V** (alimentation) | → | **3.3V** | Référence logique TTL |

⚠️ **Important** : Respecter strictement les échanges **RX↔TX** (croisement) et connecter **GND** en priorité pour éviter les dommages.

**Visualisation des connexions (Mermaid)**

```mermaid
graph LR
    subgraph USB["🔌 Adaptateur USB-TTL"]
        RX["RX<br/>(Entrée)"]
        TX["TX<br/>(Sortie)"]
        GND_USB["GND<br/>(Masse)"]
        VCC["3.3V<br/>(Alimentation)"]
    end
    
    subgraph ESP["🔧 Module ESP32"]
        GPIO3["GPIO3<br/>RX"]
        GPIO1["GPIO1<br/>TX"]
        GND_ESP["GND<br/>(Masse)"]
        VCC_ESP["3.3V<br/>(Alimentation)"]
    end
    
    RX -->|Croisement| GPIO1
    TX -->|Croisement| GPIO3
    GND_USB -->|PRIORITAIRE| GND_ESP
    VCC -->|Référence| VCC_ESP
    
    style RX fill:#ff9999,color:#000
    style TX fill:#ff9999,color:#000
    style GND_USB fill:#ffcc99,color:#000
    style VCC fill:#99ff99,color:#000
    style GPIO3 fill:#99ccff,color:#000
    style GPIO1 fill:#99ccff,color:#000
    style GND_ESP fill:#ffcc99,color:#000
    style VCC_ESP fill:#99ff99,color:#000
```

### 2) Module MOS-ESP32-UART

**Role principal**

- Exposer l'API REST locale via Wi-Fi.
- Commander la sortie MOS/relais qui active ou coupe le gestionnaire de charge.

**Usage dans ce projet**

- `POST /api/status/[PIN]` : lecture et supervision de l'etat de sortie.
- `POST /api/power/[PIN]` : bascule (toggle) de l'etat pour la charge.
- Integration mDNS : acces via `http://POWER_SWITCH`.

**Recommandations de cablage (principe)**

- Sortie GPIO autorisee de l'ESP32 -> entree commande du module MOS/relais.
- Alimentation ESP32 stable (USB/5V selon carte), masse commune avec la partie commande.
- Ligne de puissance du chargeur separee de la logique TTL (isolement conseille selon montage).

### Sequence de mise en service (materiel)

1. Connecter le **USB-TTL** et flasher le firmware ESP32.
2. Ouvrir le moniteur serie et verifier IP + mDNS (`POWER_SWITCH`).
3. Brancher le **MOS-ESP32-UART** sur la ligne de commande du gestionnaire de charge.
4. Configurer l'app Android (`URL`, `PIN`, `token`, seuils `MIN/MAX`).
5. Verifier les reponses API `STATUS` puis `POWER`.

## USAGE:

1. **Démarrer MOS ESP32**
    - Brancher l'ESP32 sur une source d'alimentation.
    - Si aucune configuration n'est enregistrée, la LED (GPIO 23) clignote lentement.
    - Le module crée un point d'accès nommé **ESP32-CONFIG**.

2. **Configurer le WiFi (Portail Captif)**
    - Connectez votre smartphone au réseau WiFi **ESP32-CONFIG**.
    - Une notification de connexion devrait apparaître. Sinon, ouvrez votre navigateur sur `http://192.168.4.1`.
    - Saisissez le SSID et le mot de passe de votre réseau local.
    - L'ESP32 enregistre les infos en mémoire (NVS) et redémarre.
    - Une fois connecté, la LED (GPIO 23) devient fixe.

3. **Réinitialisation (Reset WiFi)**
    - Pour effacer la configuration WiFi : maintenez le bouton **BOOT** (GPIO 0) enfoncé pendant **3 secondes** au démarrage.
    - La LED clignotera rapidement pour confirmer le reset, puis l'ESP32 repassera en mode Portail Captif.

4. **(OPTION) MONITORER avec IDE ( ARDUINO )**
    - Ouvrir Arduino IDE et selectionner la connexion serie de l'USB-TTL.
    - Aller dans **Outils → Moniteur serie** (ou `Ctrl + Shift + M`).
    - Configurer la vitesse : **115200 baud**.
    - Observer les logs de boot, AP Mode, connexion WiFi et mDNS en temps reel.
    - Verifier que le token Bearer et les endpoints sont corrects.

5. **Lancer l'appli**
    - Ouvrir l'application **MS-OVH-SMS** sur le telephone Android.
    - Acceder a l'onglet **Power**.
    - Entrer les parametres:
        - **URL** : `http://POWER_SWITCH` (ou l'IP detectee automatiquement)
        - **Port** : `80` (par defaut)
        - **PIN** : le numero GPIO configure sur l'ESP32 (ex: `4`)
        - **Token** : le Bearer token du firmware (ex: `6ec3a985e5084bd0889e77c6cd1f81de`)
        - **Min** : niveau batterie minimum avant charge (ex: `20%`)
        - **Max** : niveau batterie maximum avant arret (ex: `80%`)
    - Tester manuellement les boutons **STATUS** et **POWER** pour verifier la connexion.
    - Lancer le cycle automatique (**Start Automation**).
    - Observer les logs sur l'USB-TTL pour valider les appels API.

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
curl -X POST "http://POWER_SWITCH/api/status/4" \
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
curl -X POST "http://POWER_SWITCH/api/power/4" \
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
curl -X POST "http://POWER_SWITCH/"
```

### Codes de retour utiles

- `200` : commande executee
- `400` : format endpoint invalide (ex: PIN manquante)
- `401` : token absent/invalide
- `403` : PIN non autorisee
- `404` : endpoint inconnu
- `405` : methode non autorisee (autre que POST)

## Firmware ESP32 (REST API)

### Utilité

Ce firmware transforme l'ESP32 en **serveur REST sécurisé** pour piloter à distance les sorties
GPIO. Il permet à l'application Android (MS-OVH-SMS) de :

- **Interroger l'état** d'une sortie GPIO en temps réel (`/api/status/[PIN]`)
- **Basculer (toggle)** une sortie GPIO pour ouvrir/fermer le gestionnaire de charge (
  `/api/power/[PIN]`)
- **Synchroniser** l'état matériel avec la logique métier du téléphone

### Caractéristiques principales

| Caractéristique       | Détail                                                                                |
|-----------------------|---------------------------------------------------------------------------------------|
| **Authentification**  | Bearer Token (header `Authorization: Bearer <TOKEN>`) — 401 si absent/invalide        |
| **Connexion WiFi**    | Portail Captif (AP Mode "ESP32-CONFIG") — Config via 192.168.4.1 |
| **Stockage Identifiants** | NVS via `Preferences.h` (Persistant après redémarrage) |
| **Reset Hardware**    | Bouton BOOT (GPIO 0) maintenu 3s pour effacer le WiFi |
| **Découverte Réseau** | mDNS — accessible via `http://POWER_SWITCH` |
| **Endpoints HTTP**    | `POST /api/power/[PIN]` • `POST /api/status/[PIN]` • `POST /` (info)                  |
| **Sécurité GPIO**     | Whitelist de PINs autorisées (16, 17, 23, 26, 27)                       |
| **Codes HTTP**        | 200 (OK), 400 (format), 401 (auth), 403 (PIN interdite), 404 (endpoint), 405 (méthode) |
| **Débit Série**       | 115200 baud — logs détaillés [WiFi], [AP], [STA], [AUTH], [POWER]                     |
| **LED Status**        | GPIO 23 — clignote lent (AP), fixe (STA), rapide (Reset)                              |
| **Résilience**        | Reconnexion automatique si perte WiFi • Watchdog intégré                              |

### Code source complet

```cpp
/*
 * ESP32 REST API - GPIO Control via HTTP (Captive Portal Version)
 * ==============================================================
 * Configuration WiFi via Portail Captif (Mode AP "ESP32-CONFIG")
 * Reset Hardware : Bouton BOOT (GPIO 0) maintenu 3s au démarrage
 * LED Status (GPIO 23) : Clignotement lent (AP), Fixe (STA)
 *
 * Board : ESP32 Dev Module
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ESPmDNS.h>
#include <DNSServer.h>
#include <Preferences.h>

// ─── Bearer Token ─────────────────────────────────────────────────────────────
#define BEARER_TOKEN  "6ec3a985e5084bd0889e77c6cd1f81de"
#define HOSTNAME      "POWER_SWITCH"

// ─── Configuration Hardware ───────────────────────────────────────────────────
#define LED_PIN       23
#define RESET_PIN     0    // Bouton BOOT
#define RESET_TIME_MS 3000

const int ALLOWED_PINS[] = {16, 17, 23, 26, 27};
const int ALLOWED_PINS_COUNT = sizeof(ALLOWED_PINS) / sizeof(ALLOWED_PINS[0]);

// ─── Globales ─────────────────────────────────────────────────────────────────
WebServer server(80);
DNSServer dnsServer;
Preferences preferences;
String ssid, password;
bool isAPMode = false;

// ─── Auth Bearer ──────────────────────────────────────────────────────────────
bool checkBearer() {
    if (!server.hasHeader("Authorization")) return false;
    String authHeader = server.header("Authorization");
    return authHeader.equals("Bearer " + String(BEARER_TOKEN));
}

// ─── Handlers REST ────────────────────────────────────────────────────────────
void sendJSON(int code, String json) {
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.send(code, "application/json", json);
}

int parsePinFromURI(String uri) {
    int lastSlash = uri.lastIndexOf('/');
    if (lastSlash == -1) return -1;
    String pinStr = uri.substring(lastSlash + 1);
    for (char c : pinStr) if (!isDigit(c)) return -1;
    return pinStr.toInt();
}

void handlePower() {
    if (!checkBearer()) { sendJSON(401, "{\"error\":\"Unauthorized\"}"); return; }
    int pin = parsePinFromURI(server.uri());
    bool allowed = false;
    for (int i=0; i<ALLOWED_PINS_COUNT; i++) if (ALLOWED_PINS[i] == pin) allowed = true;
    if (!allowed) { sendJSON(403, "{\"error\":\"Forbidden PIN\"}"); return; }
    
    pinMode(pin, OUTPUT);
    digitalWrite(pin, !digitalRead(pin));
    int val = digitalRead(pin);
    sendJSON(200, "{\"pin\":" + String(pin) + ",\"state\":\"" + (val?"HIGH":"LOW") + "\",\"value\":" + String(val) + ",\"action\":\"toggled\"}");
}

void handleStatus() {
    if (!checkBearer()) { sendJSON(401, "{\"error\":\"Unauthorized\"}"); return; }
    int pin = parsePinFromURI(server.uri());
    bool allowed = false;
    for (int i=0; i<ALLOWED_PINS_COUNT; i++) if (ALLOWED_PINS[i] == pin) allowed = true;
    if (!allowed) { sendJSON(403, "{\"error\":\"Forbidden PIN\"}"); return; }
    
    int val = digitalRead(pin);
    sendJSON(200, "{\"pin\":" + String(pin) + ",\"state\":\"" + (val?"HIGH":"LOW") + "\",\"value\":" + String(val) + "}");
}

// ─── Portail Captif ───────────────────────────────────────────────────────────
void handleCaptivePortal() {
    if (server.hasArg("ssid") && server.hasArg("pass")) {
        preferences.begin("wifi", false);
        preferences.putString("ssid", server.arg("ssid"));
        preferences.putString("pass", server.arg("pass"));
        preferences.end();
        server.send(200, "text/html", "<html><body><h1>Config OK</h1><p>Redemarrage...</p></body></html>");
        delay(2000);
        ESP.restart();
    }
    String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'></head><body>"
                  "<h2>ESP32 WiFi Config</h2><form method='POST'>"
                  "SSID: <input name='ssid'><br>PASS: <input name='pass' type='password'><br>"
                  "<input type='submit' value='Enregistrer'></form></body></html>";
    server.send(200, "text/html", html);
}

// ─── Setup ────────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    pinMode(RESET_PIN, INPUT_PULLUP);

    // Check Reset Hardware
    if (digitalRead(RESET_PIN) == LOW) {
        unsigned long start = millis();
        while (digitalRead(RESET_PIN) == LOW && (millis() - start < RESET_TIME_MS)) {
            digitalWrite(LED_PIN, (millis()/100)%2); // Fast blink
        }
        if (millis() - start >= RESET_TIME_MS) {
            preferences.begin("wifi", false);
            preferences.clear();
            preferences.end();
            Serial.println("[RESET] WiFi Cleared");
            for(int i=0; i<10; i++) { digitalWrite(LED_PIN, !digitalRead(LED_PIN)); delay(50); }
        }
    }

    preferences.begin("wifi", true);
    ssid = preferences.getString("ssid", "");
    password = preferences.getString("pass", "");
    preferences.end();

    if (ssid == "") {
        isAPMode = true;
        WiFi.softAP("ESP32-CONFIG");
        dnsServer.start(53, "*", WiFi.softAPIP());
        server.on("/", HTTP_GET, handleCaptivePortal);
        server.on("/", HTTP_POST, handleCaptivePortal);
        server.onNotFound(handleCaptivePortal);
        Serial.println("[AP] Mode Config : ESP32-CONFIG / 192.168.4.1");
    } else {
        WiFi.begin(ssid.c_str(), password.c_str());
        Serial.print("[STA] Connection a " + ssid);
        int retry = 0;
        while (WiFi.status() != WL_CONNECTED && retry < 20) {
            digitalWrite(LED_PIN, !digitalRead(LED_PIN));
            delay(500); Serial.print("."); retry++;
        }
        if (WiFi.status() == WL_CONNECTED) {
            digitalWrite(LED_PIN, HIGH);
            MDNS.begin(HOSTNAME);
            const char* headerKeys[] = {"Authorization"};
            server.collectHeaders(headerKeys, 1);
            server.on("/", HTTP_POST, [](){ sendJSON(200, "{\"device\":\""+String(HOSTNAME)+"\"}"); });
            server.onNotFound([]() {
                if (server.uri().startsWith("/api/power/")) handlePower();
                else if (server.uri().startsWith("/api/status/")) handleStatus();
                else sendJSON(404, "{\"error\":\"Not Found\"}");
            });
            Serial.println("\n[OK] IP: " + WiFi.localIP().toString());
        } else {
            ESP.restart(); // Retry
        }
    }
    server.begin();
}

void loop() {
    if (isAPMode) {
        dnsServer.processNextRequest();
        digitalWrite(LED_PIN, (millis()/1000)%2); // Slow blink
    }
    server.handleClient();
}
```

