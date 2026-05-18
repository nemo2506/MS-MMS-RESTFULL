# Manuel d'achat et d'integration - Module MOS + ESP32

## 1) Objectif
Mettre en place un pilotage d'alimentation securise via relais / MOSFET avec un ESP32, puis flasher le firmware depuis Arduino IDE pour piloter les seuils batterie (Min / Max), la coupure de charge et le controle BLE / Wi-Fi.

## 2) Liste d'achat (BOM)
- 1x carte ESP32 DevKit (USB-C ou micro-USB)
- 1x module MOSFET logique (ex: IRLZ44N / IRLZ34N en carte driver) ou module relais DC adapte a la charge
- 1x convertisseur DC-DC (buck) stable pour alimentation ESP32 (5V -> 3.3V selon design)
- 1x diode de roue libre (si charge inductive)
- 1x fusible en ligne + porte-fusible
- 1x interrupteur general
- Cables, borniers, gaine thermo, boitier
- Option recommandee : module mesure batterie (INA219 / INA226) pour telemetrie fiable

## 3) Criteres de selection securite
- Courant nominal du module MOS / relais >= 2x courant reel de la charge
- Tension max supportee > tension batterie + marge
- Dissipation thermique verifiee (radiateur si necessaire)
- Protection court-circuit et inversion polarite
- Masse commune propre entre ESP32 et module de puissance

## 4) Schema de principe (niveau fonctionnel)
1. Batterie -> fusible -> module MOS / relais -> charge
2. ESP32 GPIO -> entree commande du module MOS / relais
3. ESP32 alimente via buck stable
4. Capteur batterie (option) -> ESP32 (I2C / ADC)

Important : ne jamais alimenter directement une charge forte depuis un pin GPIO.

## 5) Flash Arduino IDE (procedure)

## 5.1 Preparation
- Installer Arduino IDE 2.x
- Ajouter l'URL des cartes ESP32 dans Preferences
- Installer `esp32 by Espressif Systems` via Boards Manager
- Selectionner la carte et le port COM
- Installer les bibliotheques necessaires si elles ne sont pas deja presentes :
  - `BLE`
  - `WiFi`
  - `WebServer`
  - `BLE2902`

## 5.2 Parametres recommandes
- Board : ESP32 Dev Module (ou modele exact)
- Flash Frequency : 80MHz
- Partition Scheme : Default 4MB with SPIFFS (ou selon firmware)
- Upload Speed : 921600 (reduire si instable)
- Monitor Speed : 115200

## 5.3 Upload
1. Brancher l'ESP32 en USB
2. Ouvrir le sketch
3. Compiler
4. Televerser
5. Ouvrir le Serial Monitor pour verifier les logs BLE, Wi-Fi et l'etat du relais

### Illustration Arduino IDE

Capture d'ecran de reference pour la configuration / compilation dans Arduino IDE :

![Arduino IDE](../screenShots/6.%20ARDUINO%20IDE.png "Arduino IDE - configuration et flash ESP32")

## 5.4 Alignement avec l'application Android

L'application Android lit des parametres BLE locaux via `local.properties`.
Les valeurs configurees cote firmware doivent rester coherentes avec :

- `ble.deviceName`
- `ble.serviceUuid`
- `ble.characteristicUuid`
- `ble.pin`
- `ble.writeType`

Exemple documente et obfusque :

```ini
# BLE local parameters
ble.deviceName=ESP32_S*****
ble.serviceUuid=6f1c2a90-****-****-****-********d5b1
ble.characteristicUuid=a4d91c2e-****-****-****-********9d73
ble.pin=****
# BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT = 2
ble.writeType=2
```

## 5.5 Exemple de firmware ESP32 (BLE + relais + Wi-Fi)

Le sketch suivant montre une implementation type pour :

- exposer un service BLE securise par PIN
- piloter un relais via BLE
- activer / desactiver un serveur HTTP local
- notifier l'etat courant a l'application Android
- transmettre l'adresse IP locale via notification BLE quand le Wi-Fi est actif

> ÔÜá´©Å Important
> Ne jamais publier dans le depot les vrais secrets Wi-Fi, PIN ou identifiants finaux.
> Utiliser uniquement des placeholders ou des valeurs obfusquees dans la documentation.

```cpp
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEAdvertising.h>
#include <esp_bt_device.h>
#include <WiFi.h>
#include <WebServer.h>
#include <BLE2902.h>

// ÔöÇÔöÇ CONFIG ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
#define RELAY_PIN   4
#define BLE_PIN     0000
#define DEVICE_NAME "ESP32_SWITCH"

// Remplacer par vos valeurs locales hors depot
#define WIFI_SSID "<WIFI_SSID>"
#define WIFI_PASS "<WIFI_PASS>"

// Remplacer par les UUID reels utilises par l'application Android
#define SERVICE_UUID        "<BLE_SERVICE_UUID>"
#define CHARACTERISTIC_UUID "<BLE_CHARACTERISTIC_UUID>"

// ÔöÇÔöÇ STATE ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
BLECharacteristic *pCharacteristic = nullptr;
BLEServer         *pServer         = nullptr;
WebServer          server(80);

volatile bool deviceConnected = false;
volatile bool relayState      = false;
volatile bool wifiEnabled     = false;

uint32_t cmdCount        = 0;
uint32_t connectionCount = 0;

// ÔöÇÔöÇ FORWARD DECLARATIONS ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void stopWiFiServer();
void startWiFiServer();

// ÔöÇÔöÇ RELAY SAFE CONTROL ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void setRelay(bool on) {
  relayState = on;
  digitalWrite(RELAY_PIN, on ? HIGH : LOW);

  if (pCharacteristic) {
	pCharacteristic->setValue(on ? "1" : "0");
	pCharacteristic->notify();
  }

  Serial.println(on ? "RELAY ON" : "RELAY OFF");
}

// ÔöÇÔöÇ PAGE WEB ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void handleRoot() {
  String label   = relayState ? "ON" : "OFF";
  String btnTxt  = relayState ? "Eteindre" : "Allumer";
  String btnHref = relayState ? "/off" : "/on";

  String html = R"rawhtml(
<!DOCTYPE html>
<html lang="fr" data-bs-theme="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ESP32 Switch</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
  <meta http-equiv="refresh" content="5">
</head>
<body class="bg-dark text-light min-vh-100 d-flex align-items-center justify-content-center">
  <div class="container text-center" style="max-width: 400px;">
	<h1 class="mb-4"><i class="bi bi-toggles"></i> ESP32 Switch</h1>
	<div class="card bg-secondary-subtle border-0 shadow rounded-4 p-4 mb-4">
	  <p class="text-uppercase text-muted small mb-1">Etat du relais</p>
	  <span class="badge rounded-pill fs-5 px-4 py-2 mb-4 )rawhtml" + String(relayState ? "bg-success" : "bg-danger") + R"rawhtml(">
		<i class="bi bi-power"></i> )rawhtml" + label + R"rawhtml(
	  </span>
	  <a href=")rawhtml" + btnHref + R"rawhtml(" class="btn btn-lg )rawhtml" + String(relayState ? "btn-outline-danger" : "btn-success") + R"rawhtml( rounded-3 mb-3">
		<i class="bi )rawhtml" + String(relayState ? "bi-toggle-off" : "bi-toggle-on") + R"rawhtml("></i>
		)rawhtml" + btnTxt + R"rawhtml(
	  </a>
	  <a href="/wifi-off" class="btn btn-outline-warning rounded-3">
		<i class="bi bi-wifi-off"></i> Eteindre WiFi
	  </a>
	</div>
	<div class="card bg-secondary-subtle border-0 shadow rounded-4 p-3">
	  <ul class="list-group list-group-flush bg-transparent text-start small">
		<li class="list-group-item bg-transparent text-light border-secondary d-flex justify-content-between">
		  <span><i class="bi bi-bluetooth text-primary"></i> Commandes BLE</span>
		  <span class="badge bg-primary rounded-pill">)rawhtml" + String(cmdCount) + R"rawhtml(</span>
		</li>
		<li class="list-group-item bg-transparent text-light border-secondary d-flex justify-content-between">
		  <span><i class="bi bi-phone text-info"></i> Connexions BLE</span>
		  <span class="badge bg-info rounded-pill">)rawhtml" + String(connectionCount) + R"rawhtml(</span>
		</li>
		<li class="list-group-item bg-transparent text-light border-0 d-flex justify-content-between">
		  <span><i class="bi bi-wifi text-success"></i> Adresse IP</span>
		  <span class="text-success fw-bold">)rawhtml" + WiFi.localIP().toString() + R"rawhtml(</span>
		</li>
	  </ul>
	</div>
	<p class="text-muted small mt-3">
	  <i class="bi bi-arrow-clockwise"></i> Rafraichissement auto toutes les 5s
	</p>
  </div>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
)rawhtml";

  server.send(200, "text/html; charset=utf-8", html);
}

void handleOn() {
  setRelay(true);
  server.sendHeader("Location", "/");
  server.send(303);
}

void handleOff() {
  setRelay(false);
  server.sendHeader("Location", "/");
  server.send(303);
}

void handleWifiOff() {
  server.send(200, "text/html; charset=utf-8",
	"<!DOCTYPE html>"
	"<html lang='fr' data-bs-theme='dark'>"
	"<head>"
	"<meta charset='UTF-8'>"
	"<meta name='viewport' content='width=device-width, initial-scale=1'>"
	"<title>ESP32 Switch</title>"
	"<link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css' rel='stylesheet'>"
	"<link href='https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css' rel='stylesheet'>"
	"</head>"
	"<body class='bg-dark text-light min-vh-100 d-flex align-items-center justify-content-center'>"
	"<div class='container text-center' style='max-width:400px'>"
	"<div class='card bg-secondary-subtle border-0 shadow rounded-4 p-4'>"
	"<i class='bi bi-wifi-off fs-1 text-warning mb-3'></i>"
	"<h4>WiFi en cours d&apos;arret&hellip;</h4>"
	"<p class='text-muted'>Reconnectez-vous via BLE<br>"
	"<span class='badge bg-primary'>commande W</span> pour le reactiver.</p>"
	"</div></div>"
	"</body></html>");
  delay(500);
  stopWiFiServer();
}

void handleNotFound() {
  server.send(404, "text/plain", "Not found");
}

// ÔöÇÔöÇ WIFI START / STOP ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  Serial.print("WiFi connexion");
  uint8_t tries = 0;

  while (WiFi.status() != WL_CONNECTED && tries < 20) {
	delay(500);
	Serial.print(".");
	tries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
	Serial.printf("\nWiFi OK - IP: %s\n", WiFi.localIP().toString().c_str());
  } else {
	Serial.println("\nWiFi ECHEC");
  }
}

void startWiFiServer() {
  connectWiFi();
  if (WiFi.status() != WL_CONNECTED) return;

  server.on("/", handleRoot);
  server.on("/on", handleOn);
  server.on("/off", handleOff);
  server.on("/wifi-off", handleWifiOff);
  server.onNotFound(handleNotFound);
  server.begin();
  wifiEnabled = true;

  if (pCharacteristic) {
	String ip = "IP:" + WiFi.localIP().toString();
	pCharacteristic->setValue(ip.c_str());
	pCharacteristic->notify();
  }

  Serial.println("Serveur HTTP demarre");
}

void stopWiFiServer() {
  server.stop();
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);
  wifiEnabled = false;

  if (pCharacteristic) {
	pCharacteristic->setValue(relayState ? "1" : "0");
	pCharacteristic->notify();
  }

  Serial.println("Serveur HTTP arrete");
}

// ÔöÇÔöÇ BLE SECURITY ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
class MySecurity : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override {
	Serial.println("PassKey Request");
	return BLE_PIN;
  }

  void onPassKeyNotify(uint32_t pass_key) override {
	Serial.printf("PassKey Notify: %lu\n", pass_key);
  }

  bool onConfirmPIN(uint32_t pass_key) override {
	Serial.printf("Confirm PIN: %lu\n", pass_key);
	return pass_key == BLE_PIN;
  }

  bool onSecurityRequest() override {
	Serial.println("Security Request");
	return true;
  }

  void onAuthenticationComplete(esp_ble_auth_cmpl_t cmpl) override {
	if (cmpl.success) {
	  Serial.println("BLE Auth OK");
	} else {
	  Serial.printf("BLE Auth ECHEC - raison: %d\n", cmpl.fail_reason);
	}
  }
};

// ÔöÇÔöÇ CALLBACK CONNECT ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pSrv) override {
	deviceConnected = true;
	connectionCount++;
	Serial.printf("Client connecte #%lu\n", connectionCount);
  }

  void onDisconnect(BLEServer* pSrv) override {
	deviceConnected = false;
	Serial.println("Client deconnecte");
	setRelay(false);
	delay(300);
	pSrv->startAdvertising();
  }
};

// ÔöÇÔöÇ CALLBACK WRITE ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
class CharCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
	std::string raw = pChar->getValue();
	String value = raw.c_str();
	if (value.length() != 1) return;

	char cmd = value[0];
	cmdCount++;
	Serial.printf("CMD #%lu = %c\n", cmdCount, cmd);

	if      (cmd == '1' && !relayState)  setRelay(true);
	else if (cmd == '0' &&  relayState)  setRelay(false);
	else if (cmd == 'W' && !wifiEnabled) startWiFiServer();
	else if (cmd == 'X' &&  wifiEnabled) stopWiFiServer();
  }
};

// ÔöÇÔöÇ BLE MAC ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void printBleMac() {
  const uint8_t* mac = esp_bt_dev_get_address();
  if (!mac) {
	Serial.println("BLE MAC not ready");
	return;
  }

  Serial.printf("BLE MAC: %02X:%02X:%02X:%02X:%02X:%02X\n",
				mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
}

// ÔöÇÔöÇ SETUP ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void setup() {
  Serial.begin(115200);

  Serial.printf("Flash: %u MB\n", ESP.getFlashChipSize() / (1024 * 1024));
  Serial.printf("PSRAM: %s\n", psramFound() ? "YES" : "NO");

  pinMode(RELAY_PIN, OUTPUT);
  setRelay(false);

  BLEDevice::init(DEVICE_NAME);
  BLEDevice::setSecurityCallbacks(new MySecurity());

  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLESecurity *pSecurity = new BLESecurity();
  pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_MITM_BOND);
  pSecurity->setCapability(ESP_IO_CAP_OUT);
  pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

  BLEService *service = pServer->createService(SERVICE_UUID);
  pCharacteristic = service->createCharacteristic(
	CHARACTERISTIC_UUID,
	BLECharacteristic::PROPERTY_WRITE |
	BLECharacteristic::PROPERTY_READ  |
	BLECharacteristic::PROPERTY_NOTIFY
  );

  pCharacteristic->setValue("0");
  pCharacteristic->addDescriptor(new BLE2902());
  pCharacteristic->setCallbacks(new CharCallbacks());
  service->start();

  printBleMac();

  BLEAdvertising *adv = BLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->setScanResponse(true);
  adv->start();

  Serial.println("BLE SWITCH READY");
}

// ÔöÇÔöÇ LOOP ÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇÔöÇ
void loop() {
  if (wifiEnabled) {
	server.handleClient();

	if (WiFi.status() != WL_CONNECTED) {
	  Serial.println("WiFi perdu, reconnexion...");
	  connectWiFi();
	}
  }

  delay(10);
}
```

## 5.6 Commandes BLE supportees
- `1` : active le relais
- `0` : coupe le relais
- `W` : active le Wi-Fi et demarre le serveur HTTP
- `X` : coupe le Wi-Fi et arrete le serveur HTTP

## 5.7 Flux de fonctionnement
1. L'application Android se connecte en BLE au module `ESP32_SWITCH`
2. Elle ecrit une commande dans la caracteristique GATT
3. L'ESP32 execute l'action demandee :
   - bascule relais
   - demarrage / arret Wi-Fi
4. L'etat courant est renvoye via `notify()`
5. Si le Wi-Fi est actif, l'ESP32 peut notifier `IP:<adresse_locale>`

## 5.8 Recommandations de securite pour la documentation
- Ne pas publier de SSID / mot de passe Wi-Fi reels dans le depot
- Ne pas publier le vrai PIN BLE en clair dans la documentation
- Eviter de logger des secrets dans le moniteur serie
- Aligner les UUID BLE entre firmware ESP32 et application Android
- Stocker les valeurs reelles uniquement dans les fichiers locaux hors versionning

## 6) Strategie "alimentation planifiee" pour securite batterie
- Seuil bas (Min) : si batterie <= Min, activer la source secourue / relais selon votre logique
- Seuil haut (Max) : si batterie >= Max, desactiver la charge / relais pour eviter surcharge
- Hysteresis recommande (ex : 5%) pour eviter oscillations rapides
- Delai anti-rebond (ex : 10 a 30s) avant chaque bascule
- Journaliser chaque transition (timestamp, tension, action)

## 7) Checklist integration Android / BLE
- Verifier UUID service / caracteristique cote ESP32
- Verifier format payload BLE (UTF-8 / commandes)
- Tester : lecture etat, relais ON / OFF, Wi-Fi ON / OFF
- Tester seuils Min / Max avec batterie simulee
- Valider timeout et reprise apres deconnexion BLE
- Verifier que `ble.deviceName`, `ble.serviceUuid`, `ble.characteristicUuid` et `ble.pin` restent coherents entre Android et ESP32

## 8) Validation avant production
- Test charge nominale sur 2h minimum
- Test de perte BLE / reboot ESP32
- Test de securite : coupure / reprise alimentation
- Verification echauffement MOS / relais
- Verification logs d'evenements
- Validation du comportement si le Wi-Fi tombe pendant une commande distante

## 9) Notes conformite
Ce document donne une trame technique. Adapter au materiel reel, aux contraintes electriques et aux regles de securite de votre installation.
