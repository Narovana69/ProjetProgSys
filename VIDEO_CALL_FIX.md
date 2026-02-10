# Correction des Appels Vidéo Multiples

## Problème Identifié
Votre système d'appels vidéo avait un problème : **quand vous faisiez un appel vidéo, d'autres appels entrants pouvaient créer plusieurs fenêtres simultanément**, causant des erreurs et une mauvaise expérience utilisateur.

## Cause Racine
1. **Pas de contrôle d'état d'appel** : Aucune vérification globale pour s'assurer qu'un seul appel est actif
2. **Pas de gestionnaire centralisé** : Chaque instance était indépendante sans synchronisation
3. **Nettoyage incomplet** : Les références aux fenêtres fermées n'étaient pas correctement libérées

## Solution Implémentée

### 1. **Création d'un Gestionnaire d'Appels Vidéo** (`VideoCallManager.java`)
Un gestionnaire singleton thread-safe qui centralise la gestion d'état des appels vidéo :

```java
public class VideoCallManager {
    // Singleton pour garantir une seule instance
    private static final VideoCallManager instance = new VideoCallManager();
    
    // Vérifie si un appel est actif
    public boolean isCallActive()
    
    // Démarre un nouvel appel (échoue si un autre est déjà actif)
    public synchronized boolean startCall(VideoCallWindow window)
    
    // Récupère l'appel actif
    public VideoCallWindow getActiveCall()
    
    // Termine l'appel courant
    public synchronized void endCall()
}
```

### 2. **Amélioration de VideoCallWindow**
- Ajout d'un callback `onWindowClosed` qui s'exécute quand la fenêtre se ferme
- Méthode `setOnWindowClosed(Runnable callback)` pour enregistrer le callback
- Le callback s'exécute automatiquement lors de la fermeture, permettant au gestionnaire de nettoyer l'état

### 3. **Refactorisation de ChatWindow.startVideoCall()**
```java
private void startVideoCall() {
    // ✓ Vérifier si un appel est déjà actif
    if (VideoCallManager.getInstance().isCallActive()) {
        // Afficher l'appel existant en avant
        VideoCallWindow existingCall = VideoCallManager.getInstance().getActiveCall();
        if (existingCall != null) {
            existingCall.getStage().toFront();
        }
        showTemporaryMessage("⚠️ Un appel est déjà en cours");
        return;
    }
    
    // ✓ Créer la nouvelle fenêtre
    this.videoCallWindow = new VideoCallWindow(...);
    
    // ✓ Enregistrer auprès du gestionnaire (échoue si un appel est actif)
    if (VideoCallManager.getInstance().startCall(this.videoCallWindow)) {
        this.videoCallWindow.connect();
        this.videoCallWindow.show();
    } else {
        this.videoCallWindow.disconnect();
        showTemporaryMessage("⚠️ Un autre appel est déjà en cours");
    }
}
```

## Avantages de la Solution

✅ **Un seul appel actif à la fois** - Les appels multiples sont impossibles  
✅ **Thread-safe** - Utilise la synchronisation pour éviter les conditions de course  
✅ **Nettoyage automatique** - Les ressources sont libérées quand la fenêtre se ferme  
✅ **Gracieux** - Si un appel est en cours, l'utilisateur est informé et peut le rejoindre  
✅ **Scalable** - Architecture centralisée facile à étendre  

## Fichiers Modifiés

1. **VideoCallWindow.java**
   - Ajout du champ `private Runnable onWindowClosed`
   - Modification du gestionnaire `stage.setOnCloseRequest()`
   - Ajout de la méthode `setOnWindowClosed(Runnable callback)`

2. **ChatWindow.java**
   - Refactorisation complète de `startVideoCall()`
   - Intégration avec `VideoCallManager`

3. **VideoCallManager.java** (nouveau fichier)
   - Gestionnaire singleton d'état d'appel

## Test Recommandé

1. Lancez l'application
2. Commencez un appel vidéo
3. Tentez de lancer un nouvel appel → **Message d'avertissement**
4. Fermez l'appel
5. Lancez un nouvel appel → **Fonctionne normalement**

---
**Date** : 27 janvier 2026  
**Status** : ✅ Implémenté et compilé avec succès
