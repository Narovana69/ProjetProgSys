package com.reseau.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.reseau.common.UserInfo;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

/**
 * ChatWindow - Main chat interface
 */
public class ChatWindow {
    private static final int MAX_MESSAGES = 500;

    private Stage stage;
    private Client client;
    // ✅ Configuration dynamique - peut être changée sans redémarrer
    private String serverHost;
    private int videoPort;
    private int audioPort;
    private TextArea messageDisplay;
    private TextField messageInput;
    private Button sendButton;
    private VBox userListContainer;
    private Map<String, Label> userLabels;
    private int messageCount = 0;
    private VideoCallWindow videoCallWindow;
    
    // Multi-panel view system
    private BorderPane mainContent;
    private VBox chatView;
    private String currentView = "chat";
    
    // Private chat system (Discord-style)
    private String currentPrivateChatUser = null;
    private VBox privateMessagesContainer;
    private ScrollPane privateMessagesScrollPane;
    private TextField privateMessageInput;
    private int privateMessageCount = 0;
    private String lastPrivateMessageSender = "";
    private List<String> dmContacts = new ArrayList<>();
    private VBox dmListContainer;
    
    // Store private message history per user
    private Map<String, List<PrivateMessageData>> privateMessageHistory = new HashMap<>();
    
    // Helper class to store message data
    private static class PrivateMessageData {
        String sender;
        String text;
        String timestamp;
        boolean isOwnMessage;
        
        PrivateMessageData(String sender, String text, String timestamp, boolean isOwnMessage) {
            this.sender = sender;
            this.text = text;
            this.timestamp = timestamp;
            this.isOwnMessage = isOwnMessage;
        }
    }
    
    // Discord-like colors
    private static final String DISCORD_BG_MAIN = "#313338";
    private static final String DISCORD_BG_SIDE = "#1e1f22";
    private static final String DISCORD_BG_NAVBAR = "#111214";
    private static final String DISCORD_BG_HOVER = "#2e3035";
    private static final String DISCORD_TEXT_NORMAL = "#dbdee1";
    private static final String DISCORD_TEXT_MUTED = "#949ba4";
    private static final String DISCORD_BRAND = "#5865f2";
    private static final String DISCORD_ONLINE = "#23a55a";

    public ChatWindow(Stage stage, Client client) {
        this.stage = stage;
        this.client = client;
        this.userLabels = new HashMap<>();
        // ✅ Charger la configuration
        ClientConfig config = ClientConfig.getInstance();
        config.printConfig();
        this.serverHost = config.getServerHost();
        this.videoPort = config.getVideoPort();
        this.audioPort = config.getAudioPort();
        setupUI();
        setupMessageListener();
        loadDmContactsFromHistory();

        // Request initial user list after a short delay (on a background thread to avoid blocking FX)
        Thread initThread = new Thread(() -> {
            try {
                Thread.sleep(500); // Wait for connection to stabilize
                if (client != null && client.isConnected()) {
                    client.refreshUserList();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "InitUserList");
        initThread.setDaemon(true);
        initThread.start();
    }

    private void setupUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");

        // Discord-style top bar
        HBox topBar = new HBox(12);
        topBar.setPadding(new Insets(12, 20, 12, 15));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: " + DISCORD_BG_NAVBAR + ";");
        
        Label iconLabel = new Label("💬");
        iconLabel.setStyle("-fx-font-size: 22px;");

        Label titleLabel = new Label("NEXO");
        titleLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #ffffff;");
        
        Label separator = new Label("|");
        separator.setStyle("-fx-text-fill: " + DISCORD_TEXT_MUTED + "; -fx-font-size: 16px;");
        
        Label usernameLabel = new Label(client.getUsername());
        usernameLabel.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-font-weight: 500; " +
                        "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";");

        topBar.getChildren().addAll(iconLabel, titleLabel, separator, usernameLabel);

        // Main content area with multi-panel support
        mainContent = new BorderPane();
        
        // Build chat view
        chatView = buildChatView();
        
        // Show chat view by default
        mainContent.setCenter(chatView);

        // Assemble layout
        root.setTop(topBar);
        root.setCenter(mainContent);

        Scene scene = new Scene(root, 1000, 600);
        stage.setScene(scene);
        stage.setTitle("NEXO - " + client.getUsername());
        stage.setMinWidth(800);
        stage.setMinHeight(400);
        stage.setResizable(true);  // Permet de redimensionner la fenêtre
        stage.setMaximized(true);  // Fenêtre agrandie par défaut

        // Handle window close - properly disconnect and exit
        stage.setOnCloseRequest(e -> {
            shutdown();
        });
    }
    

    
    /**
     * Switch between different views (chat, profile, settings)
     */
    private void switchView(String viewName) {
        currentView = viewName;
        
        switch (viewName) {
            case "chat":
                currentPrivateChatUser = null;
                mainContent.setCenter(chatView);
                break;
            case "dms":
                requestUserListUpdate();
                if (!dmContacts.isEmpty()) {
                    // Ouvrir directement le dernier contact DM
                    String lastContact = currentPrivateChatUser != null && dmContacts.contains(currentPrivateChatUser)
                        ? currentPrivateChatUser : dmContacts.get(dmContacts.size() - 1);
                    currentPrivateChatUser = lastContact;
                    currentView = "private_chat";
                    lastPrivateMessageSender = "";
                    privateMessageCount = 0;
                    mainContent.setCenter(buildPrivateChatView(lastContact));
                    Platform.runLater(() -> loadStoredPrivateMessages(lastContact));
                } else {
                    // Pas de contacts DM : afficher un placeholder
                    currentPrivateChatUser = null;
                    mainContent.setCenter(buildEmptyDmsView());
                }
                break;
            case "private_chat":
                // Private chat view is set separately with openPrivateChat()
                break;
        }
    }
    
    /**
     * Open a private chat with a specific user (Discord-style embedded view)
     */
    private void openPrivateChat(String username) {
        // Silently ignore if user somehow tries to chat with themselves
        if (username.equals(client.getUsername())) {
            return;
        }
        
        currentPrivateChatUser = username;
        currentView = "private_chat";
        
        // Add to DM contacts if not already there
        if (!dmContacts.contains(username)) {
            dmContacts.add(username);
        }
        
        // Reset message state for new conversation
        lastPrivateMessageSender = "";
        privateMessageCount = 0;
        
        // Build and show the private chat view
        mainContent.setCenter(buildPrivateChatView(username));
        
        // Load stored messages after view is built
        Platform.runLater(() -> loadStoredPrivateMessages(username));
    }
    
    /**
     * Vue vide pour quand il n'y a aucun contact DM.
     */
    private HBox buildEmptyDmsView() {
        HBox container = new HBox();
        container.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");

        // ========== LEFT NAVBAR ==========
        VBox navbar = buildDiscordNavbar();

        // ========== MAIN AREA ==========
        VBox mainArea = new VBox(12);
        mainArea.setAlignment(Pos.CENTER);
        mainArea.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        HBox.setHgrow(mainArea, Priority.ALWAYS);

        Label icon = new Label("💬");
        icon.setStyle("-fx-font-size: 48px;");

        Label title = new Label("Aucune conversation privée");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + DISCORD_TEXT_NORMAL + ";");

        Label hint = new Label("Retourne au chat général et clique\nsur un utilisateur en ligne pour commencer.");
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: " + DISCORD_TEXT_MUTED + "; -fx-text-alignment: center;");

        Button goBackBtn = new Button("🏠  Retour au chat");
        goBackBtn.setStyle(
            "-fx-background-color: " + DISCORD_BRAND + "; " +
            "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; " +
            "-fx-background-radius: 6; -fx-padding: 10 24; -fx-cursor: hand;"
        );
        goBackBtn.setOnAction(e -> switchView("chat"));

        mainArea.getChildren().addAll(icon, title, hint, goBackBtn);

        container.getChildren().addAll(navbar, mainArea);
        return container;
    }

    /**
     * Build the private chat view (Discord-style with navbar and sidebar)
     */
    private HBox buildPrivateChatView(String username) {
        HBox container = new HBox();
        container.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        // ========== LEFT NAVBAR (Icons) ==========
        VBox navbar = buildDiscordNavbar();
        
        // ========== SIDEBAR (DM List) ==========
        VBox sidebar = buildDmSidebar(username);
        
        // ========== MAIN CHAT AREA ==========
        BorderPane chatArea = buildPrivateChatArea(username);
        HBox.setHgrow(chatArea, Priority.ALWAYS);
        
        container.getChildren().addAll(navbar, sidebar, chatArea);
        
        return container;
    }
    
    /**
     * Build Discord-style navbar for DM view (same buttons as global navbar)
     */
    private VBox buildDiscordNavbar() {
        VBox navbar = new VBox(8);
        navbar.setPrefWidth(72);
        navbar.setMinWidth(72);
        navbar.setMaxWidth(72);
        navbar.setAlignment(Pos.TOP_CENTER);
        navbar.setPadding(new Insets(12, 0, 12, 0));
        navbar.setStyle("-fx-background-color: " + DISCORD_BG_NAVBAR + ";");
        
        // Home - global chat
        Button homeBtn = createDiscordNavButton("🏠", "General Chat", false);
        homeBtn.setOnAction(e -> switchView("chat"));
        
        // DM indicator (active in this view)
        Button dmBtn = createDiscordNavButton("💬", "Direct Messages", true);
        
        // Separator
        Region separator = new Region();
        separator.setPrefHeight(2);
        separator.setMaxWidth(32);
        separator.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 1;");
        VBox.setMargin(separator, new Insets(4, 0, 4, 0));
        
        // Video call
        Button videoBtn = createDiscordNavButton("📹", "Video Call", false);
        videoBtn.setOnAction(e -> startVideoCall());
        
        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        // Logout
        Button logoutBtn = createDiscordNavButton("🚪", "Logout", false);
        logoutBtn.setOnAction(e -> handleLogout());
        
        // User control panel at bottom
        VBox userPanel = buildUserControlPanel();
        
        navbar.getChildren().addAll(homeBtn, dmBtn, separator, videoBtn, spacer, logoutBtn, userPanel);
        
        return navbar;
    }
    
    /**
     * Create Discord-style navigation button
     */
    private Button createDiscordNavButton(String icon, String tooltip, boolean active) {
        Button btn = new Button();
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: " + (active ? "white" : DISCORD_TEXT_MUTED) + ";");
        btn.setGraphic(iconLabel);
        btn.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        btn.setPrefSize(48, 48);
        btn.setMinSize(48, 48);
        btn.setMaxSize(48, 48);
        
        String bgColor = active ? DISCORD_BRAND : "#36393f";
        String radius = active ? "14" : "24";
        
        btn.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: " + radius + "; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;"
        );
        
        btn.setTooltip(new Tooltip(tooltip));
        
        final String finalBgColor = bgColor;
        final String finalRadius = radius;
        final boolean isActive = active;
        btn.setOnMouseEntered(e -> {
            btn.setStyle(
                "-fx-background-color: " + DISCORD_BRAND + "; " +
                "-fx-background-radius: 14; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 0;"
            );
            iconLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: white;");
        });
        
        btn.setOnMouseExited(e -> {
            btn.setStyle(
                "-fx-background-color: " + finalBgColor + "; " +
                "-fx-background-radius: " + finalRadius + "; " +
                "-fx-cursor: hand; " +
                "-fx-padding: 0;"
            );
            iconLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: " + (isActive ? "white" : DISCORD_TEXT_MUTED) + ";");
        });
        
        return btn;
    }
    
    /**
     * Build user control panel for navbar bottom
     */
    private VBox buildUserControlPanel() {
        VBox panel = new VBox(8);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #232428;");
        panel.setPrefWidth(72);
        
        // Mini avatar with status dot
        StackPane avatarStack = new StackPane();
        
        Label avatar = new Label(getAvatarEmoji(client.getUsername()));
        avatar.setStyle("-fx-font-size: 24px;");
        
        Circle statusDot = new Circle(6);
        statusDot.setStyle("-fx-fill: " + DISCORD_ONLINE + "; -fx-stroke: #232428; -fx-stroke-width: 2;");
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        
        avatarStack.getChildren().addAll(avatar, statusDot);
        
        panel.getChildren().addAll(avatarStack);
        
        return panel;
    }
    
    /**
     * Build DM sidebar with contact list
     */
    private VBox buildDmSidebar(String activeUsername) {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setStyle("-fx-background-color: " + DISCORD_BG_SIDE + ";");
        
        // Search bar
        HBox searchContainer = new HBox();
        searchContainer.setPadding(new Insets(10));
        
        TextField searchField = new TextField();
        searchField.setPromptText("Find or start a conversation");
        searchField.setPrefHeight(30);
        searchField.setStyle(
            "-fx-background-color: " + DISCORD_BG_NAVBAR + "; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-background-radius: 4; " +
            "-fx-border-width: 0; " +
            "-fx-font-size: 13px; " +
            "-fx-padding: 5 10;"
        );
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchContainer.getChildren().add(searchField);
        
        // DM Header
        HBox dmHeader = new HBox();
        dmHeader.setPadding(new Insets(15, 10, 5, 15));
        dmHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label dmTitle = new Label("DIRECT MESSAGES");
        dmTitle.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";"
        );
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button addDmBtn = new Button("+");
        addDmBtn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 16px; " +
            "-fx-cursor: hand;"
        );
        addDmBtn.setTooltip(new Tooltip("Create DM"));
        
        dmHeader.getChildren().addAll(dmTitle, spacer, addDmBtn);
        
        // DM List container
        dmListContainer = new VBox(2);
        dmListContainer.setPadding(new Insets(5, 8, 5, 8));
        
        // Add current chat partner as active
        addDmContactEntry(activeUsername, true);
        
        // Add other DM contacts
        for (String contact : dmContacts) {
            if (!contact.equals(activeUsername)) {
                addDmContactEntry(contact, false);
            }
        }
        
        ScrollPane dmScroll = new ScrollPane(dmListContainer);
        dmScroll.setFitToWidth(true);
        dmScroll.setStyle(
            "-fx-background: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-border-width: 0;"
        );
        VBox.setVgrow(dmScroll, Priority.ALWAYS);
        
        sidebar.getChildren().addAll(searchContainer, dmHeader, dmScroll);
        
        return sidebar;
    }
    
    /**
     * Add a DM contact entry to sidebar
     */
    private void addDmContactEntry(String username, boolean active) {
        HBox entry = new HBox(12);
        entry.setPadding(new Insets(8, 10, 8, 10));
        entry.setAlignment(Pos.CENTER_LEFT);
        
        String bgColor = active ? DISCORD_BG_HOVER : "transparent";
        entry.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: 4; " +
            "-fx-cursor: hand;"
        );
        
        // Avatar with status — vérifier la présence réelle
        boolean isOnline = userLabels.containsKey(username);
        StackPane avatarStack = new StackPane();
        avatarStack.setMinSize(32, 32);
        avatarStack.setMaxSize(32, 32);
        
        Label avatar = new Label(getAvatarEmoji(username));
        avatar.setStyle("-fx-font-size: 20px;");
        
        Circle statusDot = new Circle(5);
        String dotColor = isOnline ? DISCORD_ONLINE : "#747f8d";
        statusDot.setStyle("-fx-fill: " + dotColor + "; -fx-stroke: " + DISCORD_BG_SIDE + "; -fx-stroke-width: 2;");
        StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
        
        avatarStack.getChildren().addAll(avatar, statusDot);
        
        // Username
        Label nameLabel = new Label(username);
        nameLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: 500; " +
            "-fx-text-fill: " + (active ? "#ffffff" : DISCORD_TEXT_NORMAL) + ";"
        );
        
        // Close button
        Region entrySpacer = new Region();
        HBox.setHgrow(entrySpacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("✕");
        closeBtn.setVisible(false);
        closeBtn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 12px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 2 5;"
        );
        
        entry.getChildren().addAll(avatarStack, nameLabel, entrySpacer, closeBtn);
        
        // Click to switch chat
        entry.setOnMouseClicked(e -> {
            if (!active) {
                openPrivateChat(username);
            }
        });
        
        // Hover effects
        entry.setOnMouseEntered(e -> {
            if (!active) {
                entry.setStyle(
                    "-fx-background-color: " + DISCORD_BG_HOVER + "; " +
                    "-fx-background-radius: 4; " +
                    "-fx-cursor: hand;"
                );
            }
            closeBtn.setVisible(true);
        });
        
        entry.setOnMouseExited(e -> {
            if (!active) {
                entry.setStyle(
                    "-fx-background-color: transparent; " +
                    "-fx-background-radius: 4; " +
                    "-fx-cursor: hand;"
                );
            }
            closeBtn.setVisible(false);
        });
        
        dmListContainer.getChildren().add(entry);
    }

    /**
     * Charge les contacts DM depuis l'historique des messages priv\u00e9s.
     * Appel\u00e9 au d\u00e9marrage pour restaurer les bulles DM m\u00eame apr\u00e8s reconnexion.
     */
    private void loadDmContactsFromHistory() {
        for (String contact : privateMessageHistory.keySet()) {
            if (!dmContacts.contains(contact)) {
                dmContacts.add(contact);
            }
        }
    }

    /**
     * Construit la section "MESSAGES PRIV\u00c9S" dans le sidebar du chat global,
     * listant tous les contacts DM persistants (m\u00eame hors-ligne).
     */
    private VBox buildGlobalSidebarDmSection() {
        VBox section = new VBox(2);
        section.setPadding(new Insets(12, 8, 5, 8));

        if (dmContacts.isEmpty()) {
            return section; // rien \u00e0 afficher
        }

        Label dmHeader = new Label("MESSAGES PRIV\u00c9S");
        dmHeader.setStyle(
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-padding: 0 0 6 6;"
        );
        section.getChildren().add(dmHeader);

        for (String contact : dmContacts) {
            boolean isOnline = userLabels.containsKey(contact);
            HBox entry = new HBox(10);
            entry.setPadding(new Insets(6, 8, 6, 8));
            entry.setAlignment(Pos.CENTER_LEFT);
            entry.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-background-radius: 6; -fx-cursor: hand;"
            );

            // Avatar
            StackPane avatarStack = new StackPane();
            avatarStack.setMinSize(28, 28);
            avatarStack.setMaxSize(28, 28);
            Label avatar = new Label(getAvatarEmoji(contact));
            avatar.setStyle("-fx-font-size: 16px;");
            Circle statusDot = new Circle(4.5);
            String dotColor = isOnline ? DISCORD_ONLINE : "#747f8d";
            statusDot.setStyle("-fx-fill: " + dotColor + "; -fx-stroke: " + DISCORD_BG_SIDE + "; -fx-stroke-width: 2;");
            StackPane.setAlignment(statusDot, Pos.BOTTOM_RIGHT);
            avatarStack.getChildren().addAll(avatar, statusDot);

            // Name
            Label nameLabel = new Label(contact);
            nameLabel.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-text-fill: " + (isOnline ? DISCORD_TEXT_NORMAL : DISCORD_TEXT_MUTED) + ";"
            );

            entry.getChildren().addAll(avatarStack, nameLabel);
            entry.setOnMouseClicked(e -> openPrivateChat(contact));
            entry.setOnMouseEntered(e -> entry.setStyle(
                "-fx-background-color: " + DISCORD_BG_HOVER + "; " +
                "-fx-background-radius: 6; -fx-cursor: hand;"
            ));
            entry.setOnMouseExited(e -> entry.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-background-radius: 6; -fx-cursor: hand;"
            ));

            section.getChildren().add(entry);
        }

        return section;
    }

    /**
     * Build the main private chat area
     */
    private BorderPane buildPrivateChatArea(String username) {
        BorderPane chatArea = new BorderPane();
        chatArea.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        // ========== HEADER ==========
        HBox header = buildPrivateChatHeader(username);
        
        // ========== MESSAGES AREA ==========
        privateMessagesContainer = new VBox(0);
        privateMessagesContainer.setPadding(new Insets(16, 16, 8, 16));
        privateMessagesContainer.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        privateMessagesScrollPane = new ScrollPane(privateMessagesContainer);
        privateMessagesScrollPane.setFitToWidth(true);
        privateMessagesScrollPane.setStyle(
            "-fx-background: " + DISCORD_BG_MAIN + "; " +
            "-fx-background-color: " + DISCORD_BG_MAIN + "; " +
            "-fx-border-width: 0;"
        );
        VBox.setVgrow(privateMessagesScrollPane, Priority.ALWAYS);
        
        // Add welcome message
        addPrivateSystemMessage("🔒 This is the beginning of your direct message history with @" + username);
        addPrivateSystemMessage("Only you two can see these messages.");
        
        // ========== INPUT AREA ==========
        HBox inputArea = buildPrivateChatInputArea(username);
        
        chatArea.setTop(header);
        chatArea.setCenter(privateMessagesScrollPane);
        chatArea.setBottom(inputArea);
        
        return chatArea;
    }
    
    /**
     * Build private chat header
     */
    private HBox buildPrivateChatHeader(String username) {
        HBox header = new HBox(10);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: " + DISCORD_BG_MAIN + "; " +
            "-fx-border-color: #232428; " +
            "-fx-border-width: 0 0 1 0;"
        );
        
        // @ symbol
        Label atSymbol = new Label("@");
        atSymbol.setStyle(
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";"
        );
        
        // Username
        Label usernameLabel = new Label(username);
        usernameLabel.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #ffffff;"
        );
        
        // Status indicator - check real online status
        boolean isOnline = userLabels.containsKey(username);
        Circle statusDot = new Circle(4);
        statusDot.setStyle("-fx-fill: " + (isOnline ? DISCORD_ONLINE : "#747f8d") + ";");
        
        // Store reference to update when user list changes
        statusDot.setId("privateChatStatusDot_" + username);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Action buttons
        Button voiceCallBtn = createPrivateChatHeaderButton("📞", "Start Voice Call");
        voiceCallBtn.setOnAction(e -> showTemporaryMessage("🎤 Voice calls coming soon!"));
        
        Button videoCallBtn = createPrivateChatHeaderButton("📹", "Start Video Call");
        videoCallBtn.setOnAction(e -> startVideoCall());
        
        Button pinBtn = createPrivateChatHeaderButton("📌", "Pinned Messages");
        pinBtn.setOnAction(e -> showTemporaryMessage("📌 No pinned messages yet"));
        
        // Search input with functionality
        TextField searchInput = new TextField();
        searchInput.setPromptText("Search messages...");
        searchInput.setPrefWidth(180);
        searchInput.setStyle(
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-background-radius: 4; " +
            "-fx-border-width: 0; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 4 8;"
        );
        
        // Search functionality
        final String chatPartner = username;
        searchInput.setOnAction(e -> {
            String searchTerm = searchInput.getText().trim().toLowerCase();
            if (!searchTerm.isEmpty()) {
                searchPrivateMessages(chatPartner, searchTerm);
            }
        });
        
        header.getChildren().addAll(
            atSymbol, usernameLabel, statusDot,
            spacer,
            voiceCallBtn, videoCallBtn, pinBtn, searchInput
        );
        
        return header;
    }
    
    /**
     * Search through private messages
     */
    private void searchPrivateMessages(String username, String searchTerm) {
        List<PrivateMessageData> history = privateMessageHistory.get(username);
        if (history == null || history.isEmpty()) {
            showTemporaryMessage("🔍 No messages to search");
            return;
        }
        
        int found = 0;
        for (PrivateMessageData msg : history) {
            if (msg.text.toLowerCase().contains(searchTerm)) {
                found++;
            }
        }
        
        if (found > 0) {
            showTemporaryMessage("🔍 Found " + found + " message(s) containing \"" + searchTerm + "\"");
        } else {
            showTemporaryMessage("🔍 No messages found containing \"" + searchTerm + "\"");
        }
    }
    
    /**
     * Create header action button for private chat
     */
    private Button createPrivateChatHeaderButton(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-font-size: 18px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 5;"
        );
        btn.setTooltip(new Tooltip(tooltip));
        
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + DISCORD_BG_HOVER + "; " +
            "-fx-background-radius: 4; " +
            "-fx-font-size: 18px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 5;"
        ));
        
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-font-size: 18px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 5;"
        ));
        
        return btn;
    }
    
    /**
     * Build private chat input area
     */
    private HBox buildPrivateChatInputArea(String username) {
        HBox inputArea = new HBox(0);
        inputArea.setPadding(new Insets(0, 16, 24, 16));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        // Input container with rounded corners
        HBox inputContainer = new HBox(8);
        inputContainer.setPadding(new Insets(0, 16, 0, 16));
        inputContainer.setAlignment(Pos.CENTER);
        inputContainer.setStyle(
            "-fx-background-color: #383a40; " +
            "-fx-background-radius: 8;"
        );
        HBox.setHgrow(inputContainer, Priority.ALWAYS);
        
        // Add attachment button
        Button attachBtn = new Button("➕");
        attachBtn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 18px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 8;"
        );
        attachBtn.setTooltip(new Tooltip("Attach files"));
        
        // Message input
        privateMessageInput = new TextField();
        privateMessageInput.setPromptText("Message @" + username);
        privateMessageInput.setPrefHeight(44);
        privateMessageInput.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-border-width: 0; " +
            "-fx-border-color: transparent; " +
            "-fx-focus-color: transparent; " +
            "-fx-faint-focus-color: transparent; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 4 0 4 0;"
        );
        HBox.setHgrow(privateMessageInput, Priority.ALWAYS);
        
        // Emoji & other buttons
        Button gifBtn = createPrivateChatInputButton("GIF");
        Button stickerBtn = createPrivateChatInputButton("📋");
        Button emojiBtn = createPrivateChatInputButton("😊");
        
        // Send button
        Button sendBtn = new Button("➤");
        sendBtn.setStyle(
            "-fx-background-color: " + DISCORD_BRAND + "; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-background-radius: 4; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 6 12;"
        );
        sendBtn.setVisible(false);
        sendBtn.setOnAction(e -> sendPrivateMessage());
        
        // Show send button when typing
        privateMessageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            sendBtn.setVisible(!newVal.trim().isEmpty());
        });
        
        inputContainer.getChildren().addAll(attachBtn, privateMessageInput, gifBtn, stickerBtn, emojiBtn, sendBtn);
        
        // Enter key to send
        privateMessageInput.setOnAction(e -> sendPrivateMessage());
        
        inputArea.getChildren().add(inputContainer);
        
        return inputArea;
    }
    
    /**
     * Create input button for private chat
     */
    private Button createPrivateChatInputButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 14px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 8;"
        );
        
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-font-size: 14px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 8;"
        ));
        
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 14px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 8;"
        ));
        
        return btn;
    }
    
    /**
     * Send a private message
     */
    private void sendPrivateMessage() {
        if (currentPrivateChatUser == null || privateMessageInput == null) return;
        
        String message = privateMessageInput.getText().trim();
        if (!message.isEmpty()) {
            String timestamp = java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            
            // Send to specific user
            client.sendMessage(currentPrivateChatUser, message);
            
            // Store in history
            storePrivateMessage(currentPrivateChatUser, client.getUsername(), message, timestamp, true);
            
            // Display own message with timestamp
            addPrivateChatMessage(client.getUsername(), message, timestamp, true);
            
            privateMessageInput.clear();
        }
    }
    
    /**
     * Add system message to private chat
     */
    private void addPrivateSystemMessage(String text) {
        Platform.runLater(() -> {
            Label systemMsg = new Label(text);
            systemMsg.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
                "-fx-padding: 8 0;"
            );
            systemMsg.setWrapText(true);
            
            HBox container = new HBox(systemMsg);
            container.setAlignment(Pos.CENTER);
            container.setPadding(new Insets(8, 0, 8, 0));
            
            privateMessagesContainer.getChildren().add(container);
            scrollPrivateMessagesToBottom();
        });
    }
    
    /**
     * Add chat message to private chat
     */
    private void addPrivateChatMessage(String sender, String text, boolean isOwnMessage) {
        addPrivateChatMessage(sender, text, null, isOwnMessage);
    }
    
    /**
     * Add chat message to private chat with timestamp
     */
    private void addPrivateChatMessage(String sender, String text, String storedTimestamp, boolean isOwnMessage) {
        Platform.runLater(() -> {
            boolean shouldGroup = sender.equals(lastPrivateMessageSender);
            lastPrivateMessageSender = sender;
            
            String timestamp = storedTimestamp != null ? storedTimestamp : 
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            
            if (shouldGroup) {
                addGroupedPrivateMessage(text, timestamp);
            } else {
                addNewPrivateMessageBlock(sender, text, timestamp, isOwnMessage);
            }
            
            privateMessageCount++;
            scrollPrivateMessagesToBottom();
        });
    }
    
    /**
     * Add new message block with avatar (.message-container.starting)
     */
    private void addNewPrivateMessageBlock(String sender, String text, String timestamp, boolean isOwnMessage) {
        // .message-container.starting { margin-top: 16px; }
        HBox messageBlock = new HBox(16); // margin-right: 16px (avatar)
        messageBlock.setPadding(new Insets(2, 16, 2, 16)); // padding: 2px 16px
        messageBlock.setStyle("-fx-background-color: transparent;");
        VBox.setMargin(messageBlock, new Insets(16, 0, 0, 0)); // margin-top: 16px
        
        // Hover effect
        messageBlock.setOnMouseEntered(e -> messageBlock.setStyle(
            "-fx-background-color: " + DISCORD_BG_HOVER + ";"
        ));
        messageBlock.setOnMouseExited(e -> messageBlock.setStyle(
            "-fx-background-color: transparent;"
        ));
        
        // Avatar: 40x40, border-radius: 50%
        Label avatar = new Label(getAvatarEmoji(sender));
        avatar.setMinSize(40, 40);
        avatar.setMaxSize(40, 40);
        avatar.setStyle(
            "-fx-font-size: 20px; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-radius: 20; " +
            "-fx-alignment: center;"
        );
        avatar.setAlignment(Pos.CENTER);
        
        // Content wrapper
        VBox contentWrapper = new VBox(0);
        HBox.setHgrow(contentWrapper, Priority.ALWAYS);
        
        // Header: username + timestamp
        HBox msgHeader = new HBox(8);
        msgHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label usernameLabel = new Label(isOwnMessage ? "You" : sender);
        usernameLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: 600; " + // font-weight: 600
            "-fx-text-fill: " + (isOwnMessage ? "#ffffff" : getRandomUserColor(sender)) + ";"
        );
        
        Label timestampLabel = new Label("Today at " + timestamp);
        timestampLabel.setStyle(
            "-fx-font-size: 12px; " + // 0.75rem
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";"
        );
        
        msgHeader.getChildren().addAll(usernameLabel, timestampLabel);
        
        // Message body: line-height: 1.375rem
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";"
        );
        
        contentWrapper.getChildren().addAll(msgHeader, messageBody);
        messageBlock.getChildren().addAll(avatar, contentWrapper);
        
        privateMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Add grouped message (.message-container.grouped)
     */
    private void addGroupedPrivateMessage(String text, String timestamp) {
        // .message-container.grouped .content-wrapper { margin-left: 56px; }
        HBox messageBlock = new HBox(0);
        messageBlock.setPadding(new Insets(2, 16, 2, 16)); // padding: 2px 16px
        messageBlock.setStyle("-fx-background-color: transparent;");
        
        // Spacer pour aligner avec le contenu (56px = 40px avatar + 16px gap)
        StackPane spacer = new StackPane();
        spacer.setMinWidth(56);
        spacer.setMaxWidth(56);
        
        // Timestamp visible on hover (position: absolute; left: -50px)
        Label timestampHover = new Label(timestamp);
        timestampHover.setMinWidth(50);
        timestampHover.setAlignment(Pos.CENTER_RIGHT);
        timestampHover.setVisible(false);
        timestampHover.setStyle(
            "-fx-font-size: 11px; " + // 0.7rem
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-padding: 0 6 0 0;"
        );
        spacer.getChildren().add(timestampHover);
        StackPane.setAlignment(timestampHover, Pos.CENTER_RIGHT);
        
        // Message body
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";"
        );
        HBox.setHgrow(messageBody, Priority.ALWAYS);
        
        messageBlock.getChildren().addAll(spacer, messageBody);
        
        // Hover: show timestamp
        messageBlock.setOnMouseEntered(e -> {
            messageBlock.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + ";");
            timestampHover.setVisible(true);
        });
        messageBlock.setOnMouseExited(e -> {
            messageBlock.setStyle("-fx-background-color: transparent;");
            timestampHover.setVisible(false);
        });
        
        privateMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Scroll private messages to bottom
     */
    private void scrollPrivateMessagesToBottom() {
        Platform.runLater(() -> {
            if (privateMessagesScrollPane != null) {
                privateMessagesScrollPane.setVvalue(1.0);
            }
        });
    }
    
    /**
     * Get avatar emoji based on username
     */
    private String getAvatarEmoji(String username) {
        String[] avatars = {"😊", "🎮", "🎵", "🌟", "🔥", "💎", "🎨", "🚀", "🌈", "⚡"};
        int index = Math.abs(username.hashCode()) % avatars.length;
        return avatars[index];
    }
    
    /**
     * Get random color for username
     */
    private String getRandomUserColor(String username) {
        String[] colors = {
            "#e91e63", "#9c27b0", "#673ab7", "#3f51b5",
            "#2196f3", "#00bcd4", "#009688", "#4caf50",
            "#ff9800", "#ff5722", "#f44336"
        };
        int index = Math.abs(username.hashCode()) % colors.length;
        return colors[index];
    }
    

    
    /**
     * Build the chat view (main messaging interface) - Discord style
     */
    private VBox buildChatView() {
        HBox mainLayout = new HBox();
        mainLayout.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        // ========== LEFT NAVBAR (Icons) ==========
        VBox navbar = buildGlobalChatNavbar();
        
        // ========== SIDEBAR (User List) ==========
        VBox sidebar = buildUserListSidebar();
        
        // ========== MAIN CHAT AREA ==========
        BorderPane chatArea = buildGlobalChatArea();
        HBox.setHgrow(chatArea, Priority.ALWAYS);
        
        mainLayout.getChildren().addAll(navbar, sidebar, chatArea);
        
        VBox chatContainer = new VBox();
        chatContainer.getChildren().add(mainLayout);
        VBox.setVgrow(mainLayout, Priority.ALWAYS);
        
        return chatContainer;
    }
    
    /**
     * Build Discord-style navbar for global chat
     */
    private VBox buildGlobalChatNavbar() {
        VBox navbar = new VBox(8);
        navbar.setPrefWidth(72);
        navbar.setMinWidth(72);
        navbar.setMaxWidth(72);
        navbar.setAlignment(Pos.TOP_CENTER);
        navbar.setPadding(new Insets(12, 0, 12, 0));
        navbar.setStyle("-fx-background-color: " + DISCORD_BG_NAVBAR + ";");
        
        // Home button (active - global chat)
        Button homeBtn = createDiscordNavButton("🏠", "General Chat", true);
        homeBtn.setOnAction(e -> switchView("chat"));
        
        // DMs button - ouvre la vue messages privés persistants
        Button dmsBtn = createDiscordNavButton("💬", "Messages Privés", false);
        dmsBtn.setOnAction(e -> switchView("dms"));
        
        // Separator
        Region separator = new Region();
        separator.setPrefHeight(2);
        separator.setMaxWidth(32);
        separator.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 1;");
        VBox.setMargin(separator, new Insets(4, 0, 4, 0));
        
        // Video call button
        Button videoBtn = createDiscordNavButton("📹", "Video Call", false);
        videoBtn.setOnAction(e -> startVideoCall());
        
        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        // Logout button
        Button logoutBtn = createDiscordNavButton("🚪", "Logout", false);
        logoutBtn.setOnAction(e -> handleLogout());
        
        // User control panel at bottom
        VBox userPanel = buildUserControlPanel();
        
        navbar.getChildren().addAll(homeBtn, dmsBtn, separator, videoBtn, spacer, logoutBtn, userPanel);
        
        return navbar;
    }
    
    /**
     * Build user list sidebar (Discord style)
     */
    private VBox buildUserListSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setStyle("-fx-background-color: " + DISCORD_BG_SIDE + ";");
        
        // Server/Channel header
        HBox headerBox = new HBox();
        headerBox.setPadding(new Insets(15));
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setStyle("-fx-background-color: " + DISCORD_BG_SIDE + "; -fx-border-color: #232428; -fx-border-width: 0 0 1 0;");
        
        Label serverName = new Label("📡 NEXO Server");
        serverName.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #ffffff;"
        );
        headerBox.getChildren().add(serverName);
        
        // Channel section
        VBox channelSection = new VBox(2);
        channelSection.setPadding(new Insets(10, 8, 5, 8));
        
        // Text channels header
        HBox channelHeader = new HBox();
        channelHeader.setPadding(new Insets(5, 10, 5, 5));
        Label channelTitle = new Label("TEXT CHANNELS");
        channelTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + DISCORD_TEXT_MUTED + ";");
        channelHeader.getChildren().add(channelTitle);
        
        // General channel (active)
        HBox generalChannel = createChannelEntry("# general", true);
        
        channelSection.getChildren().addAll(channelHeader, generalChannel);
        
        // Online users header
        HBox usersHeader = new HBox();
        usersHeader.setPadding(new Insets(15, 10, 5, 15));
        usersHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label usersTitle = new Label("ONLINE — " + (userLabels.isEmpty() ? "0" : userLabels.size()));
        usersTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + DISCORD_TEXT_MUTED + ";");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button refreshBtn = new Button("🔄");
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + DISCORD_TEXT_MUTED + "; -fx-font-size: 12px; -fx-cursor: hand;");
        refreshBtn.setTooltip(new Tooltip("Refresh user list"));
        refreshBtn.setOnAction(e -> requestUserListUpdate());
        
        usersHeader.getChildren().addAll(usersTitle, spacer, refreshBtn);
        
        // User list container
        userListContainer = new VBox(2);
        userListContainer.setPadding(new Insets(5, 8, 5, 8));
        
        // ---- DM Contacts section (persistent) ----
        VBox dmSection = buildGlobalSidebarDmSection();
        
        VBox scrollContent = new VBox();
        scrollContent.getChildren().addAll(userListContainer, dmSection);
        
        ScrollPane userScroll = new ScrollPane(scrollContent);
        userScroll.setFitToWidth(true);
        userScroll.setStyle(
            "-fx-background: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-border-width: 0;"
        );
        VBox.setVgrow(userScroll, Priority.ALWAYS);
        
        sidebar.getChildren().addAll(headerBox, channelSection, usersHeader, userScroll);
        
        return sidebar;
    }
    
    /**
     * Create a channel entry for sidebar
     */
    private HBox createChannelEntry(String name, boolean active) {
        HBox entry = new HBox(8);
        entry.setPadding(new Insets(6, 10, 6, 10));
        entry.setAlignment(Pos.CENTER_LEFT);
        
        String bgColor = active ? DISCORD_BG_HOVER : "transparent";
        entry.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: 4; " +
            "-fx-cursor: hand;"
        );
        
        Label channelName = new Label(name);
        channelName.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: " + (active ? "bold" : "normal") + "; " +
            "-fx-text-fill: " + (active ? "#ffffff" : DISCORD_TEXT_MUTED) + ";"
        );
        
        entry.getChildren().add(channelName);
        
        entry.setOnMouseEntered(e -> {
            if (!active) entry.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 4; -fx-cursor: hand;");
        });
        entry.setOnMouseExited(e -> {
            if (!active) entry.setStyle("-fx-background-color: transparent; -fx-background-radius: 4; -fx-cursor: hand;");
        });
        
        return entry;
    }
    
    /**
     * Build the main global chat area (Discord style)
     */
    private BorderPane buildGlobalChatArea() {
        BorderPane chatArea = new BorderPane();
        chatArea.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        // ========== HEADER ==========
        HBox header = new HBox(10);
        header.setPadding(new Insets(12, 16, 12, 16));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle(
            "-fx-background-color: " + DISCORD_BG_MAIN + "; " +
            "-fx-border-color: #232428; " +
            "-fx-border-width: 0 0 1 0;"
        );
        
        Label hashSymbol = new Label("#");
        hashSymbol.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + DISCORD_TEXT_MUTED + ";");
        
        Label channelLabel = new Label("general");
        channelLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");
        
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        
        // Header action buttons
        Button pinBtn = createPrivateChatHeaderButton("📌", "Pinned Messages");
        Button membersBtn = createPrivateChatHeaderButton("👥", "Member List");
        
        TextField searchInput = new TextField();
        searchInput.setPromptText("Search");
        searchInput.setPrefWidth(150);
        searchInput.setStyle(
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-background-radius: 4; " +
            "-fx-border-width: 0; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 4 8;"
        );
        
        header.getChildren().addAll(hashSymbol, channelLabel, headerSpacer, pinBtn, membersBtn, searchInput);
        
        // ========== MESSAGES AREA ==========
        globalMessagesContainer = new VBox(0);
        globalMessagesContainer.setPadding(new Insets(16, 16, 8, 16));
        globalMessagesContainer.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        globalMessagesScrollPane = new ScrollPane(globalMessagesContainer);
        globalMessagesScrollPane.setFitToWidth(true);
        globalMessagesScrollPane.setStyle(
            "-fx-background: " + DISCORD_BG_MAIN + "; " +
            "-fx-background-color: " + DISCORD_BG_MAIN + "; " +
            "-fx-border-width: 0;"
        );
        VBox.setVgrow(globalMessagesScrollPane, Priority.ALWAYS);
        
        // Add welcome message
        Platform.runLater(() -> {
            addGlobalSystemMessage("Welcome to #general! 🎉");
            addGlobalSystemMessage("This is the beginning of the general channel.");
        });
        
        // ========== INPUT AREA ==========
        HBox inputArea = new HBox(0);
        inputArea.setPadding(new Insets(0, 16, 24, 16));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setStyle("-fx-background-color: " + DISCORD_BG_MAIN + ";");
        
        HBox inputContainer = new HBox(8);
        inputContainer.setPadding(new Insets(0, 16, 0, 16));
        inputContainer.setAlignment(Pos.CENTER);
        inputContainer.setStyle("-fx-background-color: #383a40; -fx-background-radius: 8;");
        HBox.setHgrow(inputContainer, Priority.ALWAYS);
        
        Button attachBtn = new Button("➕");
        attachBtn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-font-size: 18px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 8;"
        );
        
        messageInput = new TextField();
        messageInput.setPromptText("Message #general");
        messageInput.setPrefHeight(44);
        messageInput.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-border-width: 0; " +
            "-fx-border-color: transparent; " +
            "-fx-focus-color: transparent; " +
            "-fx-faint-focus-color: transparent; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 4 0 4 0;"
        );
        HBox.setHgrow(messageInput, Priority.ALWAYS);
        
        Button gifBtn = createPrivateChatInputButton("GIF");
        Button stickerBtn = createPrivateChatInputButton("📋");
        Button emojiBtn = createPrivateChatInputButton("😊");
        
        sendButton = new Button("➤");
        sendButton.setStyle(
            "-fx-background-color: " + DISCORD_BRAND + "; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-background-radius: 4; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 6 12;"
        );
        sendButton.setVisible(false);
        sendButton.setOnAction(e -> sendMessage());
        
        messageInput.textProperty().addListener((obs, oldVal, newVal) -> {
            sendButton.setVisible(!newVal.trim().isEmpty());
        });
        
        messageInput.setOnAction(e -> sendMessage());
        
        inputContainer.getChildren().addAll(attachBtn, messageInput, gifBtn, stickerBtn, emojiBtn, sendButton);
        inputArea.getChildren().add(inputContainer);
        
        chatArea.setTop(header);
        chatArea.setCenter(globalMessagesScrollPane);
        chatArea.setBottom(inputArea);
        
        return chatArea;
    }
    
    // Global chat message containers
    private VBox globalMessagesContainer;
    private ScrollPane globalMessagesScrollPane;
    private String lastGlobalMessageSender = "";
    private int globalMessageCount = 0;
    
    /**
     * Add system message to global chat
     */
    private void addGlobalSystemMessage(String text) {
        Platform.runLater(() -> {
            Label systemMsg = new Label(text);
            systemMsg.setStyle(
                "-fx-font-size: 13px; " +
                "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
                "-fx-padding: 8 0;"
            );
            systemMsg.setWrapText(true);
            
            HBox container = new HBox(systemMsg);
            container.setAlignment(Pos.CENTER);
            container.setPadding(new Insets(8, 0, 8, 0));
            
            globalMessagesContainer.getChildren().add(container);
            scrollGlobalMessagesToBottom();
        });
    }
    
    /**
     * Add chat message to global chat (Discord style)
     */
    private void addGlobalChatMessage(String sender, String text) {
        Platform.runLater(() -> {
            boolean shouldGroup = sender.equals(lastGlobalMessageSender);
            lastGlobalMessageSender = sender;
            
            if (shouldGroup) {
                addGroupedGlobalMessage(text);
            } else {
                addNewGlobalMessageBlock(sender, text);
            }
            
            globalMessageCount++;
            
            // Limit messages
            if (globalMessageCount > MAX_MESSAGES && globalMessagesContainer.getChildren().size() > 0) {
                globalMessagesContainer.getChildren().remove(0);
            }
            
            scrollGlobalMessagesToBottom();
        });
    }
    
    /**
     * Add new message block with avatar (.message-container.starting)
     */
    private void addNewGlobalMessageBlock(String sender, String text) {
        // .message-container.starting { margin-top: 16px; }
        HBox messageBlock = new HBox(16); // margin-right: 16px (avatar)
        messageBlock.setPadding(new Insets(2, 16, 2, 16)); // padding: 2px 16px
        messageBlock.setStyle("-fx-background-color: transparent;");
        VBox.setMargin(messageBlock, new Insets(16, 0, 0, 0)); // margin-top: 16px
        
        // Hover effect
        messageBlock.setOnMouseEntered(e -> messageBlock.setStyle(
            "-fx-background-color: " + DISCORD_BG_HOVER + ";"
        ));
        messageBlock.setOnMouseExited(e -> messageBlock.setStyle(
            "-fx-background-color: transparent;"
        ));
        
        // Avatar: 40x40, border-radius: 50%
        Label avatar = new Label(getAvatarEmoji(sender));
        avatar.setMinSize(40, 40);
        avatar.setMaxSize(40, 40);
        avatar.setStyle(
            "-fx-font-size: 20px; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-radius: 20; " +
            "-fx-alignment: center;"
        );
        avatar.setAlignment(Pos.CENTER);
        
        // Content wrapper
        VBox contentWrapper = new VBox(0);
        HBox.setHgrow(contentWrapper, Priority.ALWAYS);
        
        // Header: username + timestamp
        HBox msgHeader = new HBox(8);
        msgHeader.setAlignment(Pos.CENTER_LEFT);
        
        boolean isOwnMessage = sender.equals(client.getUsername());
        Label usernameLabel = new Label(sender);
        usernameLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: 600; " + // font-weight: 600
            "-fx-text-fill: " + (isOwnMessage ? "#ffffff" : getRandomUserColor(sender)) + ";"
        );
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        Label timestampLabel = new Label("Today at " + timestamp);
        timestampLabel.setStyle(
            "-fx-font-size: 12px; " + // 0.75rem
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";"
        );
        
        msgHeader.getChildren().addAll(usernameLabel, timestampLabel);
        
        // Message body
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";"
        );
        
        contentWrapper.getChildren().addAll(msgHeader, messageBody);
        messageBlock.getChildren().addAll(avatar, contentWrapper);
        
        globalMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Add grouped message (.message-container.grouped)
     */
    private void addGroupedGlobalMessage(String text) {
        // .message-container.grouped .content-wrapper { margin-left: 56px; }
        HBox messageBlock = new HBox(0);
        messageBlock.setPadding(new Insets(2, 16, 2, 16)); // padding: 2px 16px
        messageBlock.setStyle("-fx-background-color: transparent;");
        
        // Spacer pour aligner avec le contenu (56px = 40px avatar + 16px gap)
        StackPane spacer = new StackPane();
        spacer.setMinWidth(56);
        spacer.setMaxWidth(56);
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        
        // Timestamp visible on hover
        Label timestampHover = new Label(timestamp);
        timestampHover.setMinWidth(50);
        timestampHover.setAlignment(Pos.CENTER_RIGHT);
        timestampHover.setVisible(false);
        timestampHover.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-padding: 0 6 0 0;"
        );
        spacer.getChildren().add(timestampHover);
        StackPane.setAlignment(timestampHover, Pos.CENTER_RIGHT);
        
        // Message body
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";"
        );
        HBox.setHgrow(messageBody, Priority.ALWAYS);
        
        messageBlock.getChildren().addAll(spacer, messageBody);
        
        // Hover: show timestamp
        messageBlock.setOnMouseEntered(e -> {
            messageBlock.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + ";");
            timestampHover.setVisible(true);
        });
        messageBlock.setOnMouseExited(e -> {
            messageBlock.setStyle("-fx-background-color: transparent;");
            timestampHover.setVisible(false);
        });
        
        globalMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Scroll global messages to bottom
     */
    private void scrollGlobalMessagesToBottom() {
        Platform.runLater(() -> {
            if (globalMessagesScrollPane != null) {
                globalMessagesScrollPane.setVvalue(1.0);
            }
        });
    }

    private void setupMessageListener() {
        client.setMessageListener(new Client.MessageListener() {
            @Override
            public void onMessageReceived(String message) {
                Platform.runLater(() -> displayMessage(message));
            }

            @Override
            public void onConnectionLost() {
                Platform.runLater(() -> {
                    displayMessage("Connection lost!");
                    messageInput.setDisable(true);
                    sendButton.setDisable(true);
                });
            }
        });
    }

    private void sendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            // Send to all (broadcast)
            client.sendMessage("all", message);
            messageInput.clear();
        }
    }

    private void displayMessage(String message) {
        // Handle USER_LIST updates
        if (message.startsWith("USER_LIST")) {
            System.out.println("DEBUG: Received USER_LIST, calling updateUserList()");
            updateUserList(message);
            return;
        }
        
        // Handle message history
        if (message.startsWith("HISTORY ")) {
            displayHistoryMessage(message);
            return;
        }

        // Parse message format: MESSAGE <sender> <recipient> <text>
        if (message.startsWith("MESSAGE ")) {
            String[] parts = message.split(" ", 4);
            if (parts.length >= 4) {
                String sender = parts[1];
                String recipient = parts[2];
                String text = parts[3];
                
                String timestamp = java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                
                // Check if this is a private message
                if (!recipient.equals("all")) {
                    // Private message - determine the other user
                    String otherUser = sender.equals(client.getUsername()) ? recipient : sender;
                    boolean isOwnMessage = sender.equals(client.getUsername());
                    
                    // Skip if it's our own message (already displayed when sent)
                    if (isOwnMessage) {
                        return;
                    }
                    
                    // Store in history (only for received messages)
                    storePrivateMessage(otherUser, sender, text, timestamp, false);
                    
                    // Add to DM contacts if not there
                    if (!dmContacts.contains(otherUser)) {
                        dmContacts.add(otherUser);
                    }
                    
                    // If private chat with this user is open, display it with timestamp
                    if (currentPrivateChatUser != null && currentPrivateChatUser.equals(otherUser)) {
                        final String ts = timestamp;
                        Platform.runLater(() -> addPrivateChatMessage(sender, text, ts, false));
                    } else {
                        // Show notification for new private message
                        Platform.runLater(() -> showTemporaryMessage("🔔 New message from " + sender));
                    }
                    return;
                }
                
                // Global message - display in Discord-style chat
                // Limit message history to prevent memory issues
                if (globalMessagesContainer != null && globalMessagesContainer.getChildren().size() >= MAX_MESSAGES) {
                    globalMessagesContainer.getChildren().remove(0);
                }
                
                // Add Discord-style message
                Platform.runLater(() -> addGlobalChatMessage(sender, text));
                messageCount++;
            }
        } else {
            // System message - display in Discord style
            Platform.runLater(() -> {
                if (globalMessagesContainer != null) {
                    // System message styling
                    Label systemMsg = new Label(message);
                    systemMsg.setWrapText(true);
                    systemMsg.setStyle(
                        "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
                        "-fx-font-size: 12px; " +
                        "-fx-font-style: italic; " +
                        "-fx-padding: 5 15;");
                    globalMessagesContainer.getChildren().add(systemMsg);
                    scrollGlobalMessagesToBottom();
                }
            });
            messageCount++;
        }
    }
    
    /**
     * Convert an epoch millis timestamp string to "HH:mm" format.
     * Falls back to the raw value if it can't be parsed.
     */
    private String formatEpochTimestamp(String raw) {
        try {
            long epoch = Long.parseLong(raw);
            return java.time.Instant.ofEpochMilli(epoch)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        } catch (NumberFormatException e) {
            // Already formatted (e.g. "14:30"), return as-is
            return raw;
        }
    }
    
    /**
     * Store a private message in history
     */
    private void storePrivateMessage(String otherUser, String sender, String text, String timestamp, boolean isOwnMessage) {
        privateMessageHistory.computeIfAbsent(otherUser, k -> new ArrayList<>())
            .add(new PrivateMessageData(sender, text, timestamp, isOwnMessage));
        
        // Limit history size
        List<PrivateMessageData> history = privateMessageHistory.get(otherUser);
        if (history.size() > MAX_MESSAGES) {
            history.remove(0);
        }
        
        // Persist contact in dmContacts so it survives reconnection
        if (!dmContacts.contains(otherUser)) {
            dmContacts.add(otherUser);
        }
    }
    
    /**
     * Load stored messages when opening a private chat
     */
    private void loadStoredPrivateMessages(String username) {
        List<PrivateMessageData> history = privateMessageHistory.get(username);
        if (history != null && !history.isEmpty()) {
            for (PrivateMessageData msg : history) {
                addPrivateChatMessage(msg.sender, msg.text, msg.timestamp, msg.isOwnMessage);
            }
        }
    }

    private void updateUserList(String message) {
        // Format: USER_LIST;user1|device1|ip1|status1;user2|device2|ip2|status2; ...
        Platform.runLater(() -> {
            try {
                System.out.println("DEBUG: Parsing USER_LIST message: " + message);
                String[] parts = message.split(";");
                System.out.println("DEBUG: Found " + (parts.length - 1) + " user entries");

                Map<String, Label> newUserLabels = new HashMap<>();
                
                // Skip first part ("USER_LIST")
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].trim();
                    if (!part.isEmpty()) {
                        System.out.println("DEBUG: Parsing user entry: " + part);
                        UserInfo userInfo = UserInfo.fromString(part);
                        if (userInfo != null) {
                            Label userLabel = createUserLabel(userInfo);
                            // Si c'est soi-même, ajouter "(Moi)" et ne pas ouvrir de private chat
                            if (userInfo.getUsername().equals(client.getUsername())) {
                                userLabel.setOnMouseClicked(null);
                                VBox userBox = (VBox) userLabel.getGraphic();
                                HBox mainLine = (HBox) userBox.getChildren().get(0);
                                Label nameLbl = (Label) mainLine.getChildren().get(1);
                                nameLbl.setText(nameLbl.getText() + " (Moi)");
                            }
                            newUserLabels.put(userInfo.getUsername(), userLabel);
                            System.out.println("DEBUG: Added user: " + userInfo.getUsername());
                        } else {
                            System.err.println("DEBUG: Failed to parse user entry: " + part);
                        }
                    }
                }

                userLabels.clear();
                userLabels.putAll(newUserLabels);
                userListContainer.getChildren().setAll(newUserLabels.values());
                
                // Update private chat header status if open
                if (currentPrivateChatUser != null) {
                    updatePrivateChatStatus(currentPrivateChatUser);
                }
                
                System.out.println("DEBUG: User list updated successfully: " + userLabels.size() + " users");
            } catch (Exception e) {
                System.err.println("Error updating user list: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Update the status dot in private chat header
     */
    private void updatePrivateChatStatus(String username) {
        boolean isOnline = userLabels.containsKey(username);
        String color = isOnline ? DISCORD_ONLINE : "#747f8d";
        
        // Find the status dot in the current view
        if (mainContent.getCenter() != null) {
            mainContent.getCenter().lookupAll(".circle").forEach(node -> {
                if (node instanceof Circle && node.getId() != null && 
                    node.getId().equals("privateChatStatusDot_" + username)) {
                    ((Circle) node).setStyle("-fx-fill: " + color + ";");
                }
            });
        }
    }

    /**
     * Request current user list from server
     */
    private void requestUserListUpdate() {
        if (client != null && client.isConnected()) {
            client.refreshUserList();
        }
    }

    private Label createUserLabel(UserInfo userInfo) {
        // Status indicator with Discord colors
        String statusIcon;
        String statusColor;
        switch (userInfo.getStatus()) {
            case ONLINE:
                statusIcon = "●"; // Solid circle
                statusColor = DISCORD_ONLINE; // Green
                break;
            case INACTIVE:
                statusIcon = "●";
                statusColor = "#f0b232"; // Orange/Yellow
                break;
            case OFFLINE:
                statusIcon = "●";
                statusColor = DISCORD_TEXT_MUTED; // Gray
                break;
            default:
                statusIcon = "●";
                statusColor = DISCORD_TEXT_MUTED;
        }

        // Device icon
        String deviceIcon = getDeviceIcon(userInfo.getDevice());

        VBox userBox = new VBox(2);

        HBox mainLine = new HBox(8);
        mainLine.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label(statusIcon);
        statusLabel.setStyle(
                "-fx-font-size: 10px; " +
                "-fx-text-fill: " + statusColor + ";");

        Label nameLabel = new Label(deviceIcon + " " + userInfo.getUsername());
        nameLabel.setStyle(
                "-fx-font-size: 14px; " +
                "-fx-font-weight: 500; " +
                "-fx-text-fill: " + DISCORD_TEXT_NORMAL + ";");

        mainLine.getChildren().addAll(statusLabel, nameLabel);

        Label ipLabel = new Label("   " + userInfo.getIpAddress());
        ipLabel.setStyle(
                "-fx-font-size: 10px; " +
                "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";");

        userBox.getChildren().addAll(mainLine, ipLabel);

        Label container = new Label();
        container.setGraphic(userBox);
        container.setPrefWidth(200);
        container.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-background-radius: 4px; " +
                "-fx-padding: 6px 8px; " +
                "-fx-cursor: hand;");
        
        // Click to open private chat
        final String username = userInfo.getUsername();
        container.setOnMouseClicked(e -> {
            System.out.println("Opening private chat with: " + username);
            openPrivateChat(username);
        });
        
        // Hover effect with Discord dark theme
        container.setOnMouseEntered(e -> container.setStyle(
                "-fx-background-color: " + DISCORD_BG_HOVER + "; " +
                "-fx-background-radius: 4px; " +
                "-fx-padding: 6px 8px; " +
                "-fx-cursor: hand;"));
        container.setOnMouseExited(e -> container.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-background-radius: 4px; " +
                "-fx-padding: 6px 8px; " +
                "-fx-cursor: hand;"));

        return container;
    }

    private String getDeviceIcon(String device) {
        if (device == null)
            return "💻";
        String lower = device.toLowerCase();
        if (lower.contains("windows"))
            return "🪟";
        if (lower.contains("mac") || lower.contains("darwin"))
            return "🍎";
        if (lower.contains("linux"))
            return "🐧";
        if (lower.contains("android"))
            return "📱";
        if (lower.contains("iphone") || lower.contains("ios"))
            return "📱";
        return "💻";
    }

    private void startVideoCall() {
        // ✓ Vérifier strictement si un appel est déjà en cours
        if (VideoCallManager.getInstance().isCallActive()) {
            VideoCallWindow existingCall = VideoCallManager.getInstance().getActiveCall();
            if (existingCall != null) {
                try {
                    existingCall.getStage().toFront();
                } catch (Exception ignored) {}
                showTemporaryMessage("📞 Un appel est déjà en cours - amenez la fenêtre en avant");
                System.out.println("⚠️ Tentative de démarrer un appel alors qu'un autre est actif");
            } else {
                // State is stale, reset the manager
                VideoCallManager.getInstance().reset();
                showTemporaryMessage("⚠️ État réinitialisé, réessayez");
            }
            return;
        }
        
        System.out.println("🔄 Démarrage d'un nouvel appel vidéo...");
        showTemporaryMessage("📞 Connexion en cours...");
        
        // Créer une nouvelle fenêtre d'appel vidéo
        try {
            VideoCallWindow newCallWindow = new VideoCallWindow(
                client.getUsername(),
                serverHost,
                videoPort,
                audioPort
            );
            
            // Essayer de démarrer l'appel via le gestionnaire
            if (VideoCallManager.getInstance().startCall(newCallWindow)) {
                System.out.println("✅ Appel accepté par le gestionnaire");
                
                // Mettre à jour la référence locale
                this.videoCallWindow = newCallWindow;
                
                // ✅ show() appelle connect() - ne pas appeler deux fois !
                newCallWindow.show();
                
                System.out.println("✅ Appel vidéo démarré avec succès");
            } else {
                // L'appel n'a pas pu être démarré
                System.err.println("❌ Le gestionnaire a rejeté l'appel");
                showTemporaryMessage("❌ Un autre appel est déjà en cours. Fermez-le d'abord.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du démarrage de l'appel: " + e.getMessage());
            e.printStackTrace();
            VideoCallManager.getInstance().reset();
            showTemporaryMessage("❌ Erreur: " + e.getMessage());
        }
    }

    
    /**
     * Handle logout - return to login window
     * Disconnects on a daemon thread to avoid blocking the FX thread,
     * then switches back to the login window on the FX thread.
     */
    private void handleLogout() {
        // Disable UI immediately to prevent double-clicks
        if (messageInput != null) messageInput.setDisable(true);
        if (sendButton != null) sendButton.setDisable(true);
        
        Thread logoutThread = new Thread(() -> {
            try {
                System.out.println("Logging out...");
                
                // Close video call if open
                if (videoCallWindow != null) {
                    try {
                        videoCallWindow.disconnect();
                    } catch (Exception e) {
                        System.err.println("Error closing video call: " + e.getMessage());
                    }
                    videoCallWindow = null;
                }
                
                // Disconnect client (sends DISCONNECT, closes streams)
                if (client != null && client.isConnected()) {
                    client.disconnect();
                }
                
            } catch (Exception ex) {
                System.err.println("Error during logout: " + ex.getMessage());
            } finally {
                // Always return to login window, even if disconnect failed
                Platform.runLater(() -> {
                    AuthLoginWindow loginWindow = new AuthLoginWindow(stage);
                    loginWindow.show();
                });
            }
        }, "LogoutThread");
        logoutThread.setDaemon(true);
        logoutThread.start();
    }
    
    /**
     * Show temporary message (Discord-style toast notification)
     */
    private void showTemporaryMessage(String message) {
        Label tempLabel = new Label(message);
        tempLabel.setStyle(
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-padding: 15 25; " +
            "-fx-background-radius: 8; " +
            "-fx-font-size: 14px; " +
            "-fx-border-color: " + DISCORD_BRAND + "; " +
            "-fx-border-width: 0 0 0 3; " +
            "-fx-border-radius: 8;");
        
        VBox overlay = new VBox(tempLabel);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        
        BorderPane root = (BorderPane) stage.getScene().getRoot();
        root.setCenter(overlay);
        
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                Platform.runLater(() -> root.setCenter(mainContent));
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    private void shutdown() {
        Thread shutdownThread = new Thread(() -> {
            try {
                System.out.println("Shutting down chat window...");
                
                // Close video call if open
                if (videoCallWindow != null) {
                    try {
                        videoCallWindow.disconnect();
                    } catch (Exception e) {
                        System.err.println("Error closing video call: " + e.getMessage());
                    }
                    videoCallWindow = null;
                }
                
                // Disconnect client (sends DISCONNECT to server, closes streams)
                if (client != null && client.isConnected()) {
                    client.disconnect();
                }
                
                // Give threads time to cleanup
                Thread.sleep(200);
                
                // Exit application
                Platform.runLater(() -> {
                    Platform.exit();
                });
                
                // Force exit after delay
                Thread.sleep(300);
                System.exit(0);
                
            } catch (Exception ex) {
                System.err.println("Error during shutdown: " + ex.getMessage());
                System.exit(1);
            }
        }, "ShutdownThread");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }
    
    /**
     * Display a message from history with formatted timestamp
     */
    private void displayHistoryMessage(String message) {
        // Format: HISTORY <timestamp> <sender> <recipient> <text>
        String[] parts = message.split(" ", 5);
        if (parts.length >= 5) {
            try {
                String sender = parts[2];
                String recipient = parts[3];
                String text = parts[4];
                
                // Only show public (broadcast) messages in global chat history
                if (!recipient.equals("all")) {
                    // Private message from history - store it silently
                    String otherUser = sender.equals(client.getUsername()) ? recipient : sender;
                    boolean isOwn = sender.equals(client.getUsername());
                    String timestamp = formatEpochTimestamp(parts[1]);
                    storePrivateMessage(otherUser, sender, text, timestamp, isOwn);
                    return;
                }
                
                // Add Discord-style message for global chat history
                Platform.runLater(() -> addGlobalChatMessage(sender, text));
                messageCount++;
                
            } catch (Exception e) {
                System.err.println("Failed to parse history message: " + e.getMessage());
            }
        }
    }

    public void show() {
        stage.show();
    }
}
