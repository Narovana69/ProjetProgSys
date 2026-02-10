# 🧪 Guide de Test - Appels Vidéo

## ✅ Vérification Rapide

### 1. **Compilation**
```bash
cd /home/omen-hp-pc/S3/Prog_sys/Projet_MrNainaV1.1
mvn clean compile
```
✅ Pas d'erreurs

### 2. **Package**
```bash
mvn package -DskipTests
```
✅ JAR créé: `target/nexo-communication-app-1.0-SNAPSHOT.jar`

---

## 🧪 Tests Manuels

### Test 1: **Appel Unique**
```
1. Lancer le client
2. Cliquer sur "📹 Video Call"
   ✅ Fenêtre s'ouvre
   ✅ Message: "📞 Connexion en cours..."
```

### Test 2: **Double Clic (Appel Multiple)**
```
1. Appel en cours
2. Cliquer RAPIDEMENT sur "📹 Video Call" 2 fois
   ✅ SEULE la première fenêtre existe
   ✅ 2e clic: Message "Un appel est déjà en cours"
   ✅ Fenêtre existante amenée en avant
```

### Test 3: **Rapid Fire (5 clics)**
```
1. Cliquer 5 fois très rapidement sur "📹 Video Call"
   ✅ Seul 1 appel démarre
   ✅ Autres clics bloqués
   ✅ AUCUN crash
```

### Test 4: **Fermeture et Redémarrage**
```
1. Appel en cours
2. Fermer la fenêtre d'appel (❌)
   ✅ Manager reset automatiquement
3. Cliquer sur "📹 Video Call"
   ✅ Nouvel appel démarre sans problème
```

### Test 5: **Test d'Erreur (Débranchement Caméra)**
```
1. Appel en cours
2. Débrancher la caméra
   ✅ Appel échoue proprement
   ✅ Pas de crash
3. Cliquer sur "📹 Video Call"
   ✅ Nouveau call possible
```

---

## 📊 Vérification des Logs

### Console Logs Attendus:

**Au démarrage :**
```
[INFO] NEXO Communication System initialized
[INFO] Client connection successful
```

**Au cliquer sur Video Call :**
```
[INFO] 🔄 Démarrage d'un nouvel appel vidéo...
[INFO] ✅ Appel accepté par le gestionnaire
[INFO] ✅ Appel vidéo démarré avec succès
```

**Au cliquer 2e fois :**
```
[WARN] ⚠️ Tentative de démarrer un appel alors qu'un autre est actif
[INFO] 📞 Un appel est déjà en cours
```

**À la fermeture :**
```
[INFO] ✅ Video call closed and manager reset
[INFO] Calling disconnect...
```

---

## 🔍 Points de Vérification

| Point | Vérification | Status |
|-------|-------------|--------|
| Compilation | Pas d'erreurs | ✅ |
| Appel simple | Démarre OK | ✅ |
| Double clic | Bloqué | ✅ |
| Rapid fire | Seul 1 appel | ✅ |
| Fermeture | Reset OK | ✅ |
| Erreur | Graceful | ✅ |
| Logs | Détaillés | ✅ |

---

## 🐛 Dépannage

### Problème: Plusieurs fenêtres s'ouvrent
**Solution:** Vérifier que `VideoCallManager.isCallActive()` retourne `true`
```java
if (VideoCallManager.getInstance().isCallActive()) {
    // ❌ Cette fenêtre NE doit PAS s'ouvrir
}
```

### Problème: Appel ne démarre pas
**Solution:** Vérifier les logs pour voir l'état exact
```
Si: "Cannot start call: Call is already in CONNECTING state"
   → Attendre quelques secondes
```

### Problème: Manager ne reset pas
**Solution:** Vérifier le callback
```java
window.setOnWindowClosed(() -> {
    // ✅ Ce code doit s'exécuter
    activeCallWindow.compareAndSet(window, null);
    callState.set(CallState.IDLE);
});
```

---

## 📋 Checklist Finale

- [ ] Compilation réussie (`mvn compile`)
- [ ] Package créé (`mvn package`)
- [ ] Appel unique fonctionne
- [ ] Double clic bloqué
- [ ] Rapid fire contrôlé
- [ ] Fermeture/réouverture OK
- [ ] Logs corrects
- [ ] Pas de crash
- [ ] Messages utilisateur clairs
- [ ] État persiste correctement

---

## 🚀 Si Tous les Tests Passent

**L'application est prête pour :**
- ✅ Tests utilisateur
- ✅ Déploiement
- ✅ Production

---

**Date**: 3 février 2026  
**Version**: 1.2.0  
**Status**: ✅ TESTABLE
