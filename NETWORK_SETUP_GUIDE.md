# 🌐 Guide: Comment Connecter NEXO Chat sur Autre Machine

## ✅ Étape 1: Trouver l'IP du Serveur

Ouvrez un terminal **SUR LA MACHINE SERVEUR** et tapez:

```bash
# Linux/Mac
hostname -I
# ou
ip addr show | grep "inet "

# Windows
ipconfig
```

**Résultat:** Vous verrez quelque chose comme `192.168.1.100` ou `10.0.0.50`

📝 Notez cette IP !

---

## ✅ Étape 2: Modifier la Configuration Client

### Sur chaque machine client:

**Option A: Éditer le fichier de configuration (RECOMMANDÉ)**

1. Ouvrez le fichier [`.nexo_config.properties`](.nexo_config.properties )
2. Changez cette ligne:
   ```properties
   server.host=localhost
   ```
   Par (remplacez l'IP):
   ```properties
   server.host=192.168.1.100
   ```
3. Sauvegardez le fichier
4. Redémarrez l'application

### Option B: Interface graphique (À implémenter)

Ajouter un bouton Paramètres pour changer dynamiquement le serveur.

---

## 📋 Exemple Complet

**Serveur:** IP = `192.168.100.5`

**Machine Client 1:** Éditer `.nexo_config.properties`
```properties
server.host=192.168.100.5
server.port=4444
video.port=5000
audio.port=6000
```

**Machine Client 2:** Éditer `.nexo_config.properties`
```properties
server.host=192.168.100.5
server.port=4444
video.port=5000
video.port=6000
```

---

## 🔍 Dépannage

### ❌ "Connection refused" ou Erreur de connexion

1. **Vérifiez que le serveur est en cours d'exécution:**
   ```bash
   # Sur le serveur
   ./start-server.sh
   ```

2. **Vérifiez l'IP dans `.nexo_config.properties`**
   - Ne pas utiliser `localhost` pour une autre machine !
   - `localhost` = machine locale seulement

3. **Ouvrez les ports sur le serveur:**
   ```bash
   # Linux - Ouvrir les ports (opt Firewall)
   sudo ufw allow 4444,5000,6000/tcp
   ```

4. **Testez la connexion réseau:**
   ```bash
   # Sur la machine client
   ping 192.168.1.100  # Remplacer par votre IP
   
   # Testez le port du serveur
   nc -zv 192.168.1.100 4444
   ```

### ❌ L'appel vidéo ne marche pas sur une autre machine

1. Vérifiez que les ports vidéo/audio sont ouverts:
   ```bash
   # Sur le serveur
   sudo ufw allow 5000,6000/tcp
   ```

2. Vérifiez [`.nexo_config.properties`] sur le client
   - `video.port=5000`
   - `audio.port=6000`

---

## 🚀 Configuration pas à pas

### Serveur (IP = 192.168.1.100)

```bash
# 1. Démarrer le serveur
cd ~/S3/Prog_sys/Projet_MrNainaV1.1
./start-server.sh

# Output attendu:
# ✅ Serveur démarré sur le port 4444
# ✅ Serveur vidéo sur le port 5000
# ✅ Serveur audio sur le port 6000
```

### Client (sur une autre machine)

```bash
# 1. Cloner ou copier le projet
git clone <repo>
cd Projet_MrNainaV1.1

# 2. Éditer la configuration
nano .nexo_config.properties
# Changer: server.host=192.168.1.100

# 3. Lancer le client
./start-client.sh
# ou
mvn clean javafx:run
```

---

## 📌 Points Importants

| Point | Détail |
|-------|--------|
| **localhost** | ❌ Ne marche QUE sur la même machine |
| **IP réelle** | ✅ Nécessaire pour une autre machine |
| **Ports ouverts** | ✅ 4444 (TCP), 5000 (vidéo), 6000 (audio) |
| **Firewall** | ✅ Peut bloquer les connexions |
| **Configuration** | ✅ À modifier AVANT de démarrer le client |

---

## 🛠️ Vérifier la Configuration

```bash
# Afficher la configuration actuelle (dans l'app):
# Les logs affichent automatiquement:
# ════════════════════════════════════════
# 📋 Configuration NEXO Chat
# ════════════════════════════════════════
#   🖥️  Serveur: 192.168.1.100
#   🔌 Port TCP: 4444
#   📹 Port Vidéo: 5000
#   🎵 Port Audio: 6000
#   📦 App: NEXO Chat v1.2.1
# ════════════════════════════════════════
```

---

## ✅ Checklist Final

- [ ] Serveur en cours d'exécution
- [ ] IP du serveur notée
- [ ] `.nexo_config.properties` modifié avec la bonne IP
- [ ] Ports ouverts (4444, 5000, 6000)
- [ ] Firewall désactivé ou ports autorisés
- [ ] Application redémarrée
- [ ] Connexion établie ✅
- [ ] Appel vidéo fonctionne ✅

---

**Fait en:** Février 2026  
**Version:** 1.2.1  
**Status:** ✅ PRÊT POUR DÉPLOIEMENT
