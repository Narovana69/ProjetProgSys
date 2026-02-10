package com.reseau.server;

import com.reseau.common.UserAccount;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthenticationService - Handles user authentication and account management
 * Uses SHA-256 with salt for secure password hashing
 */
public class AuthenticationService {
    private static final String USER_DB_FILE = ".nexo_users.dat";
    private static final String MASTER_KEY_FILE = ".nexo_master.key";
    private static final int SALT_LENGTH = 16;
    
    private Map<String, UserAccount> accounts;
    private String masterPasswordHash;
    
    public AuthenticationService() {
        this.accounts = new ConcurrentHashMap<>();
        loadAccounts();
        loadMasterPassword();
    }
    
    /**
     * Hash password with SHA-256 and salt
     */
    public String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Generate random salt
     */
    public String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Register new user account
     */
    public synchronized boolean registerUser(String username, String password, String firstName, String lastName) {
        if (accounts.containsKey(username)) {
            return false; // Username already exists
        }
        
        String salt = generateSalt();
        String passwordHash = salt + ":" + hashPassword(password, salt);
        
        UserAccount account = new UserAccount(username, passwordHash, firstName, lastName);
        accounts.put(username, account);
        saveAccounts();
        
        System.out.println("New user registered: " + username + " (" + firstName + " " + lastName + ")");
        return true;
    }
    
    /**
     * Authenticate user login
     */
    public synchronized UserAccount authenticate(String username, String password) {
        UserAccount account = accounts.get(username);
        if (account == null) {
            return null;
        }
        
        String[] parts = account.getPasswordHash().split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        
        String salt = parts[0];
        String storedHash = parts[1];
        String providedHash = hashPassword(password, salt);
        
        if (storedHash.equals(providedHash)) {
            account.updateLastLogin();
            saveAccounts();
            return account;
        }
        
        return null;
    }
    
    /**
     * Check if username exists
     */
    public boolean usernameExists(String username) {
        return accounts.containsKey(username);
    }
    
    /**
     * Verify master password for admin access
     */
    public boolean verifyMasterPassword(String password) {
        if (masterPasswordHash == null) {
            // First time setup - set master password
            String salt = generateSalt();
            masterPasswordHash = salt + ":" + hashPassword(password, salt);
            saveMasterPassword();
            return true;
        }
        
        String[] parts = masterPasswordHash.split(":", 2);
        if (parts.length != 2) {
            return false;
        }
        
        String salt = parts[0];
        String storedHash = parts[1];
        String providedHash = hashPassword(password, salt);
        
        return storedHash.equals(providedHash);
    }
    
    /**
     * Save accounts to encrypted file
     */
    private void saveAccounts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(USER_DB_FILE))) {
            oos.writeObject(accounts);
        } catch (IOException e) {
            System.err.println("Error saving user accounts: " + e.getMessage());
        }
    }
    
    /**
     * Load accounts from file
     */
    @SuppressWarnings("unchecked")
    private void loadAccounts() {
        File file = new File(USER_DB_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            accounts = (Map<String, UserAccount>) ois.readObject();
            System.out.println("Loaded " + accounts.size() + " user accounts");
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading user accounts: " + e.getMessage());
        }
    }
    
    /**
     * Save master password hash
     */
    private void saveMasterPassword() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(MASTER_KEY_FILE))) {
            writer.println(masterPasswordHash);
        } catch (IOException e) {
            System.err.println("Error saving master password: " + e.getMessage());
        }
    }
    
    /**
     * Load master password hash
     */
    private void loadMasterPassword() {
        File file = new File(MASTER_KEY_FILE);
        if (!file.exists()) {
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            masterPasswordHash = reader.readLine();
        } catch (IOException e) {
            System.err.println("Error loading master password: " + e.getMessage());
        }
    }
    
    /**
     * Get user account by username
     */
    public UserAccount getAccount(String username) {
        return accounts.get(username);
    }
}
