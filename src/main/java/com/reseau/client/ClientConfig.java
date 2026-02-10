package com.reseau.client;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * ClientConfig - Gestion centralisée de la configuration NEXO
 * Permet de changer l'adresse du serveur dynamiquement sans redémarrage
 */
public class ClientConfig {
    private static ClientConfig instance;
    private Properties props;
    private static final String CONFIG_FILE = ".nexo_config.properties";
    
    private ClientConfig() {
        props = new Properties();
        loadConfig();
    }
    
    public static synchronized ClientConfig getInstance() {
        if (instance == null) {
            instance = new ClientConfig();
        }
        return instance;
    }
    
    /**
     * Charger la configuration depuis le fichier
     */
    private void loadConfig() {
        try {
            if (Files.exists(Paths.get(CONFIG_FILE))) {
                props.load(new FileInputStream(CONFIG_FILE));
                System.out.println("✅ Configuration chargée depuis " + CONFIG_FILE);
            } else {
                createDefaultConfig();
            }
        } catch (IOException e) {
            System.err.println("⚠️ Erreur lors du chargement: " + e.getMessage());
            createDefaultConfig();
        }
    }
    
    /**
     * Créer un fichier de configuration par défaut
     */
    private void createDefaultConfig() {
        props.setProperty("server.host", "localhost");
        props.setProperty("server.port", "4444");
        props.setProperty("video.port", "5000");
        props.setProperty("audio.port", "6000");
        props.setProperty("app.name", "NEXO Chat");
        props.setProperty("app.version", "1.2.1");
        
        saveConfig();
        System.out.println("📝 Fichier de configuration créé avec valeurs par défaut");
    }
    
    /**
     * Sauvegarder la configuration dans le fichier
     */
    public synchronized void saveConfig() {
        try {
            props.store(new FileOutputStream(CONFIG_FILE), 
                       "Configuration NEXO Chat - Editez ce fichier pour changer le serveur");
            System.out.println("💾 Configuration sauvegardée");
        } catch (IOException e) {
            System.err.println("❌ Erreur lors de la sauvegarde: " + e.getMessage());
        }
    }
    
    // ✅ Getters - Utilisent la configuration dynamique
    public String getServerHost() {
        return props.getProperty("server.host", "localhost");
    }
    
    public int getServerPort() {
        try {
            return Integer.parseInt(props.getProperty("server.port", "4444"));
        } catch (NumberFormatException e) {
            return 4444;
        }
    }
    
    public int getVideoPort() {
        try {
            return Integer.parseInt(props.getProperty("video.port", "5000"));
        } catch (NumberFormatException e) {
            return 5000;
        }
    }
    
    public int getAudioPort() {
        try {
            return Integer.parseInt(props.getProperty("audio.port", "6000"));
        } catch (NumberFormatException e) {
            return 6000;
        }
    }
    
    public String getAppName() {
        return props.getProperty("app.name", "NEXO Chat");
    }
    
    public String getAppVersion() {
        return props.getProperty("app.version", "1.2.1");
    }
    
    // ✅ Setters - Sauvegardent la config
    public synchronized void setServerHost(String host) {
        props.setProperty("server.host", host);
        saveConfig();
        System.out.println("✅ Serveur défini à: " + host);
    }
    
    public synchronized void setServerPort(int port) {
        props.setProperty("server.port", String.valueOf(port));
        saveConfig();
        System.out.println("✅ Port serveur défini à: " + port);
    }
    
    public synchronized void setVideoPort(int port) {
        props.setProperty("video.port", String.valueOf(port));
        saveConfig();
        System.out.println("✅ Port vidéo défini à: " + port);
    }
    
    public synchronized void setAudioPort(int port) {
        props.setProperty("audio.port", String.valueOf(port));
        saveConfig();
        System.out.println("✅ Port audio défini à: " + port);
    }
    
    /**
     * Afficher toute la configuration
     */
    public void printConfig() {
        System.out.println("\n════════════════════════════════════════");
        System.out.println("📋 Configuration NEXO Chat");
        System.out.println("════════════════════════════════════════");
        System.out.println("  🖥️  Serveur: " + getServerHost());
        System.out.println("  🔌 Port TCP: " + getServerPort());
        System.out.println("  📹 Port Vidéo: " + getVideoPort());
        System.out.println("  🎵 Port Audio: " + getAudioPort());
        System.out.println("  📦 App: " + getAppName() + " v" + getAppVersion());
        System.out.println("════════════════════════════════════════\n");
    }
}
