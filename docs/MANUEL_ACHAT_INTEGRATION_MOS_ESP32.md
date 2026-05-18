# Manuel d'achat et d'integration - Module MOS + ESP32

## 1) Objectif
Mettre en place un pilotage d'alimentation securise via relais/MOSFET avec un ESP32, puis flasher le firmware depuis Arduino IDE pour piloter les seuils batterie (Min/Max) et la coupure de charge.

## 2) Liste d'achat (BOM)
- 1x carte ESP32 DevKit (USB-C ou micro-USB)
- 1x module MOSFET logique (ex: IRLZ44N/IRLZ34N en carte driver) ou module relais DC adapte a la charge
- 1x convertisseur DC-DC (buck) stable pour alimentation ESP32 (5V->3.3V selon design)
- 1x diode de roue libre (si charge inductive)
- 1x fusible en ligne + porte-fusible
- 1x interrupteur general
- Cables, borniers, gaine thermo, boitier
- Option recommandee: module mesure batterie (INA219/INA226) pour telemetrie fiable

## 3) Criteres de selection securite
- Courant nominal du module MOS/relais >= 2x courant reel de la charge
- Tension max supportee > tension batterie + marge
- Dissipation thermique verifiee (radiateur si necessaire)
- Protection court-circuit et inversion polarite
- Masse commune propre entre ESP32 et module de puissance

## 4) Schema de principe (niveau fonctionnel)
1. Batterie -> fusible -> module MOS/relais -> charge
2. ESP32 GPIO -> entree commande du module MOS/relais
3. ESP32 alimente via buck stable
4. Capteur batterie (option) -> ESP32 (I2C/ADC)

Important: ne jamais alimenter directement une charge forte depuis le pin GPIO.

## 5) Flash Arduino IDE (procedure)
## 5.1 Preparation
- Installer Arduino IDE 2.x
- Ajouter l'URL des cartes ESP32 dans Preferences
- Installer "esp32 by Espressif Systems" via Boards Manager
- Selectionner la carte et le port COM

## 5.2 Parametres recommandés
- Board: ESP32 Dev Module (ou modele exact)
- Flash Frequency: 80MHz
- Partition Scheme: Default 4MB with SPIFFS (ou selon firmware)
- Upload Speed: 921600 (reduire si instable)

## 5.3 Upload
1. Brancher l'ESP32 en USB
2. Ouvrir le sketch
3. Compiler
4. Televerser
5. Ouvrir le Serial Monitor pour verifier logs et niveau batterie

## 6) Strategie "alimentation planifiee" pour securite batterie
- Seuil bas (Min): si batterie <= Min, activer la source secourue/relais selon votre logique
- Seuil haut (Max): si batterie >= Max, desactiver la charge/relais pour eviter surcharge
- Hysteresis recommande (ex: 5%) pour eviter oscillations rapides
- Delai anti-rebond (ex: 10-30s) avant chaque bascule
- Journaliser chaque transition (timestamp, tension, action)

## 7) Checklist integration Android/BLE
- Verifier UUID service/caracteristique cote ESP32
- Verifier format payload BLE (UTF-8 / commandes)
- Tester: lecture etat, relais ON/OFF, Wi-Fi ON/OFF
- Tester seuils Min/Max avec batterie simulee
- Valider timeout et reprise apres deconnexion BLE

## 8) Validation avant production
- Test charge nominale sur 2h minimum
- Test de perte BLE / reboot ESP32
- Test de securite: coupure/reprise alimentation
- Verification echauffement MOS/relais
- Verification logs d'evenements

## 9) Notes conformite
Ce document donne une trame technique. Adapter au materiel reel, aux contraintes electriques et aux regles de securite de votre installation.
