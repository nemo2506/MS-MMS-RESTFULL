# Documentation du code ajoutee

Cette passe ajoute de la documentation inline (KDoc/comments) sur les zones sensibles BLE.

## Fichiers documentes
- `app/src/main/java/com/miseservice/smsovh/data/ble/BleRepositoryImpl.kt`
  - Documentation du pont callback GATT -> coroutine (`pendingRead`)
  - Documentation du flux de lecture `readGattCharacteristic`
- `app/src/main/java/com/miseservice/smsovh/viewmodel/MainViewModel.kt`
  - Documentation des setters de seuil batterie BLE (`setBleBatteryMin`, `setBleBatteryMax`)

## Pourquoi ces zones
- API BLE Android 33+ introduit des variantes de callback qui peuvent creer des ambiguïtés.
- Les seuils batterie sont critiques pour la logique de securite energetique.

## Convention recommandee
- KDoc obligatoire sur les methodes asynchrones critiques (BLE, reseau, persistence)
- Commentaire court uniquement pour expliquer une decision non evidente
- Eviter les commentaires redondants avec le code
