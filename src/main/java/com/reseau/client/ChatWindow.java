package com.reseau.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private VBox profileView;
    private String currentView = "chat";
    private Button chatNavButton;
    private Button profileNavButton;
    private Button settingsNavButton;
    
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
    
    // Friend system
    private Set<String> friendsList = new HashSet<>();
    private List<PendingFriendRequest> pendingFriendRequests = new ArrayList<>();
    private Map<String, Boolean> friendshipCache = new HashMap<>(); // username -> isFriend
    
    // Helper class for pending friend requests
    private static class PendingFriendRequest {
        String requestId;
        String senderUsername;
        
        PendingFriendRequest(String requestId, String senderUsername) {
            this.requestId = requestId;
            this.senderUsername = senderUsername;
        }
    }
    
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

        // Request initial user list
        Platform.runLater(() -> {
            try {
                Thread.sleep(500); // Wait for connection to stabilize
                client.refreshUserList();
                client.requestFriendsList(); // Request friends list
                client.requestPendingRequests(); // Request pending friend requests
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }

    private void setupUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f7fa;");

        // Modern top bar with gradient
        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(15, 20, 15, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(
                "-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);");

        Label iconLabel = new Label("💬");
        iconLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white;");

        Label titleLabel = new Label("NEXO - " + client.getUsername());
        titleLabel.setStyle(
                "-fx-font-size: 20px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Navigation buttons
        chatNavButton = createNavButton("💬", "Chat", true);
        chatNavButton.setOnAction(e -> switchView("chat"));
        
        profileNavButton = createNavButton("👤", "Profile", false);
        profileNavButton.setOnAction(e -> switchView("profile"));
        
        settingsNavButton = createNavButton("⚙️", "Settings", false);
        settingsNavButton.setOnAction(e -> switchView("settings"));

        Button videoCallButton = createNavButton("📹", "Video Call", false);
        videoCallButton.setStyle(
                "-fx-background-color: rgba(255,255,255,0.25); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 13px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 15px; " +
                        "-fx-padding: 8px 20px; " +
                        "-fx-cursor: hand;");
        videoCallButton.setOnAction(e -> startVideoCall());

        Label usernameLabel = new Label(client.getUsername());
        usernameLabel.setStyle(
                "-fx-font-size: 13px; " +
                        "-fx-font-weight: 500; " +
                        "-fx-text-fill: white; " +
                        "-fx-background-color: rgba(255,255,255,0.2); " +
                        "-fx-background-radius: 15px; " +
                        "-fx-padding: 5px 15px;");

        topBar.getChildren().addAll(iconLabel, titleLabel, spacer, chatNavButton, profileNavButton, settingsNavButton, videoCallButton);

        // Main content area with multi-panel support
        mainContent = new BorderPane();
        
        // Build different views
        chatView = buildChatView();
        profileView = buildProfileView();
        
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

        // Handle window close - properly disconnect and exit
        stage.setOnCloseRequest(e -> {
            shutdown();
        });
    }
    
    /**
     * Create navigation button with icon and text
     */
    private Button createNavButton(String icon, String text, boolean active) {
        Button button = new Button(icon + " " + text);
        String activeStyle = "-fx-background-color: rgba(255,255,255,0.3); ";
        String inactiveStyle = "-fx-background-color: rgba(255,255,255,0.15); ";
        
        button.setStyle(
            (active ? activeStyle : inactiveStyle) +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 15px; " +
            "-fx-padding: 8px 18px; " +
            "-fx-cursor: hand;");
        
        return button;
    }
    
    /**
     * Switch between different views (chat, profile, settings)
     */
    private void switchView(String viewName) {
        currentView = viewName;
        
        // Update navigation button styles
        updateNavButtonStyles();
        
        switch (viewName) {
            case "chat":
                currentPrivateChatUser = null;
                mainContent.setCenter(chatView);
                break;
            case "profile":
                currentPrivateChatUser = null;
                mainContent.setCenter(profileView);
                break;
            case "settings":
                currentPrivateChatUser = null;
                mainContent.setCenter(buildSettingsView());
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
        if (username.equals(client.getUsername())) {
            showTemporaryMessage("You can't chat with yourself!");
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
        
        // Update nav buttons
        updateNavButtonStyles();
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
     * Build Discord-style navbar
     */
    private VBox buildDiscordNavbar() {
        VBox navbar = new VBox(8);
        navbar.setPrefWidth(72);
        navbar.setMinWidth(72);
        navbar.setMaxWidth(72);
        navbar.setAlignment(Pos.TOP_CENTER);
        navbar.setPadding(new Insets(12, 0, 12, 0));
        navbar.setStyle("-fx-background-color: " + DISCORD_BG_NAVBAR + ";");
        
        // Home button - returns to global chat
        Button homeBtn = createDiscordNavButton("🏠", "Home (Global Chat)", false);
        homeBtn.setOnAction(e -> switchView("chat"));
        
        // DM indicator (active)
        Button dmBtn = createDiscordNavButton("💬", "Direct Messages", true);
        
        // Separator
        Region separator = new Region();
        separator.setPrefHeight(2);
        separator.setMaxWidth(32);
        separator.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 1;");
        VBox.setMargin(separator, new Insets(4, 0, 4, 0));
        
        // Spacer
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        // User control panel at bottom
        VBox userPanel = buildUserControlPanel();
        
        navbar.getChildren().addAll(homeBtn, dmBtn, separator, spacer, userPanel);
        
        return navbar;
    }
    
    /**
     * Create Discord-style navigation button
     */
    private Button createDiscordNavButton(String icon, String tooltip, boolean active) {
        Button btn = new Button(icon);
        btn.setPrefSize(48, 48);
        btn.setMinSize(48, 48);
        btn.setMaxSize(48, 48);
        
        String bgColor = active ? DISCORD_BRAND : DISCORD_BG_MAIN;
        String radius = active ? "16" : "24";
        
        btn.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: " + radius + "; " +
            "-fx-font-size: 20px; " +
            "-fx-cursor: hand; " +
            "-fx-text-fill: white;"
        );
        
        btn.setTooltip(new Tooltip(tooltip));
        
        // Hover effects
        final String finalBgColor = bgColor;
        final String finalRadius = radius;
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + DISCORD_BRAND + "; " +
            "-fx-background-radius: 16; " +
            "-fx-font-size: 20px; " +
            "-fx-cursor: hand; " +
            "-fx-text-fill: white;"
        ));
        
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + finalBgColor + "; " +
            "-fx-background-radius: " + finalRadius + "; " +
            "-fx-font-size: 20px; " +
            "-fx-cursor: hand; " +
            "-fx-text-fill: white;"
        ));
        
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
        
        // Settings icon
        Button settingsBtn = new Button("⚙️");
        settingsBtn.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-font-size: 16px; " +
            "-fx-cursor: hand;"
        );
        settingsBtn.setTooltip(new Tooltip("Settings"));
        settingsBtn.setOnAction(e -> switchView("settings"));
        
        panel.getChildren().addAll(avatarStack, settingsBtn);
        
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
        
        // Add listener to filter DM contacts in real-time
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterDmList(newVal.toLowerCase().trim());
        });
        
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
        
        // Avatar with status
        StackPane avatarStack = new StackPane();
        avatarStack.setMinSize(32, 32);
        avatarStack.setMaxSize(32, 32);
        
        Label avatar = new Label(getAvatarEmoji(username));
        avatar.setStyle("-fx-font-size: 20px;");
        
        Circle statusDot = new Circle(5);
        statusDot.setStyle("-fx-fill: " + DISCORD_ONLINE + "; -fx-stroke: " + DISCORD_BG_SIDE + "; -fx-stroke-width: 2;");
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
        
        // Status indicator
        Circle statusDot = new Circle(4);
        statusDot.setStyle("-fx-fill: " + DISCORD_ONLINE + ";");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        // Action buttons
        Button voiceCallBtn = createPrivateChatHeaderButton("📞", "Start Voice Call");
        Button videoCallBtn = createPrivateChatHeaderButton("📹", "Start Video Call");
        videoCallBtn.setOnAction(e -> startVideoCall());
        Button pinBtn = createPrivateChatHeaderButton("📌", "Pinned Messages");
        
        // Search input
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
        
        header.getChildren().addAll(
            atSymbol, usernameLabel, statusDot,
            spacer,
            voiceCallBtn, videoCallBtn, pinBtn, searchInput
        );
        
        return header;
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
            "-fx-font-size: 14px;"
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
            
            // Display own message
            addPrivateChatMessage(client.getUsername(), message, true);
            
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
        Platform.runLater(() -> {
            boolean shouldGroup = sender.equals(lastPrivateMessageSender);
            lastPrivateMessageSender = sender;
            
            if (shouldGroup) {
                addGroupedPrivateMessage(text);
            } else {
                addNewPrivateMessageBlock(sender, text, isOwnMessage);
            }
            
            privateMessageCount++;
            scrollPrivateMessagesToBottom();
        });
    }
    
    /**
     * Add new message block with avatar
     */
    private void addNewPrivateMessageBlock(String sender, String text, boolean isOwnMessage) {
        HBox messageBlock = new HBox(16);
        messageBlock.setPadding(new Insets(4, 48, 4, 0));
        messageBlock.setStyle("-fx-background-radius: 4;");
        
        // Hover effect
        messageBlock.setOnMouseEntered(e -> messageBlock.setStyle(
            "-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 4;"
        ));
        messageBlock.setOnMouseExited(e -> messageBlock.setStyle(
            "-fx-background-color: transparent; -fx-background-radius: 4;"
        ));
        
        // Avatar
        Label avatar = new Label(getAvatarEmoji(sender));
        avatar.setMinSize(40, 40);
        avatar.setMaxSize(40, 40);
        avatar.setStyle(
            "-fx-font-size: 24px; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-radius: 20; " +
            "-fx-alignment: center;"
        );
        avatar.setAlignment(Pos.CENTER);
        
        // Content wrapper
        VBox contentWrapper = new VBox(4);
        HBox.setHgrow(contentWrapper, Priority.ALWAYS);
        
        // Header
        HBox msgHeader = new HBox(8);
        msgHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label usernameLabel = new Label(isOwnMessage ? "You" : sender);
        usernameLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + (isOwnMessage ? "#ffffff" : getRandomUserColor(sender)) + ";"
        );
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        Label timestampLabel = new Label("Today at " + timestamp);
        timestampLabel.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + ";"
        );
        
        msgHeader.getChildren().addAll(usernameLabel, timestampLabel);
        
        // Message body
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-line-spacing: 2;"
        );
        
        contentWrapper.getChildren().addAll(msgHeader, messageBody);
        messageBlock.getChildren().addAll(avatar, contentWrapper);
        
        privateMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Add grouped message (same sender)
     */
    private void addGroupedPrivateMessage(String text) {
        HBox messageBlock = new HBox(16);
        messageBlock.setPadding(new Insets(0, 48, 0, 0));
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        
        Label timestampHover = new Label(timestamp);
        timestampHover.setMinWidth(40);
        timestampHover.setMaxWidth(40);
        timestampHover.setVisible(false);
        timestampHover.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-alignment: center-right; " +
            "-fx-padding: 0 5 0 0;"
        );
        timestampHover.setAlignment(Pos.CENTER_RIGHT);
        
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-line-spacing: 2;"
        );
        HBox.setHgrow(messageBody, Priority.ALWAYS);
        
        messageBlock.getChildren().addAll(timestampHover, messageBody);
        
        messageBlock.setOnMouseEntered(e -> {
            messageBlock.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 4;");
            timestampHover.setVisible(true);
        });
        messageBlock.setOnMouseExited(e -> {
            messageBlock.setStyle("-fx-background-color: transparent;");
            timestampHover.setVisible(false);
        });
        
        HBox wrapper = new HBox();
        Region spacer = new Region();
        spacer.setMinWidth(56);
        wrapper.getChildren().addAll(spacer, messageBlock);
        HBox.setHgrow(messageBlock, Priority.ALWAYS);
        
        privateMessagesContainer.getChildren().add(wrapper);
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
     * Update navigation button active states
     */
    private void updateNavButtonStyles() {
        chatNavButton.setStyle(createNavButtonStyle(currentView.equals("chat")));
        profileNavButton.setStyle(createNavButtonStyle(currentView.equals("profile")));
        settingsNavButton.setStyle(createNavButtonStyle(currentView.equals("settings")));
    }
    
    private String createNavButtonStyle(boolean active) {
        String bgColor = active ? "rgba(255,255,255,0.3)" : "rgba(255,255,255,0.15)";
        return "-fx-background-color: " + bgColor + "; " +
               "-fx-text-fill: white; " +
               "-fx-font-size: 13px; " +
               "-fx-font-weight: bold; " +
               "-fx-background-radius: 15px; " +
               "-fx-padding: 8px 18px; " +
               "-fx-cursor: hand;";
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
        
        // Friends/DM button
        Button friendsBtn = createDiscordNavButton("👥", "Friends & DMs", false);
        friendsBtn.setOnAction(e -> {
            // Could show DM list or stay in chat
        });
        
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
        
        // User control panel at bottom
        VBox userPanel = buildUserControlPanel();
        
        navbar.getChildren().addAll(homeBtn, friendsBtn, separator, videoBtn, spacer, userPanel);
        
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
        
        // Search bar for users
        HBox searchContainer = new HBox();
        searchContainer.setPadding(new Insets(10, 10, 5, 10));
        
        TextField userSearchField = new TextField();
        userSearchField.setPromptText("Search users...");
        userSearchField.setPrefHeight(28);
        userSearchField.setStyle(
            "-fx-background-color: " + DISCORD_BG_NAVBAR + "; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-prompt-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-background-radius: 4; " +
            "-fx-border-width: 0; " +
            "-fx-font-size: 12px; " +
            "-fx-padding: 5 10;"
        );
        HBox.setHgrow(userSearchField, Priority.ALWAYS);
        
        // Add listener to filter users in real-time
        userSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterGlobalUserList(newVal.toLowerCase().trim());
        });
        
        searchContainer.getChildren().add(userSearchField);
        
        // Online users header
        HBox usersHeader = new HBox();
        usersHeader.setPadding(new Insets(10, 10, 5, 15));
        usersHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label usersTitle = new Label("ONLINE USERS");
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
        
        ScrollPane userScroll = new ScrollPane(userListContainer);
        userScroll.setFitToWidth(true);
        userScroll.setStyle(
            "-fx-background: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-border-width: 0;"
        );
        VBox.setVgrow(userScroll, Priority.ALWAYS);
        
        sidebar.getChildren().addAll(headerBox, channelSection, searchContainer, usersHeader, userScroll);
        
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
            "-fx-font-size: 14px;"
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
     * Add new message block with avatar
     */
    private void addNewGlobalMessageBlock(String sender, String text) {
        HBox messageBlock = new HBox(16);
        messageBlock.setPadding(new Insets(4, 48, 4, 0));
        messageBlock.setStyle("-fx-background-radius: 4;");
        
        messageBlock.setOnMouseEntered(e -> messageBlock.setStyle(
            "-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 4;"
        ));
        messageBlock.setOnMouseExited(e -> messageBlock.setStyle(
            "-fx-background-color: transparent; -fx-background-radius: 4;"
        ));
        
        // Avatar
        Label avatar = new Label(getAvatarEmoji(sender));
        avatar.setMinSize(40, 40);
        avatar.setMaxSize(40, 40);
        avatar.setStyle(
            "-fx-font-size: 24px; " +
            "-fx-background-color: " + DISCORD_BG_SIDE + "; " +
            "-fx-background-radius: 20; " +
            "-fx-alignment: center;"
        );
        avatar.setAlignment(Pos.CENTER);
        
        // Content wrapper
        VBox contentWrapper = new VBox(4);
        HBox.setHgrow(contentWrapper, Priority.ALWAYS);
        
        // Header
        HBox msgHeader = new HBox(8);
        msgHeader.setAlignment(Pos.CENTER_LEFT);
        
        boolean isOwnMessage = sender.equals(client.getUsername());
        Label usernameLabel = new Label(sender);
        usernameLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: " + (isOwnMessage ? "#ffffff" : getRandomUserColor(sender)) + ";"
        );
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        Label timestampLabel = new Label("Today at " + timestamp);
        timestampLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + DISCORD_TEXT_MUTED + ";");
        
        msgHeader.getChildren().addAll(usernameLabel, timestampLabel);
        
        // Message body
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-line-spacing: 2;"
        );
        
        contentWrapper.getChildren().addAll(msgHeader, messageBody);
        messageBlock.getChildren().addAll(avatar, contentWrapper);
        
        globalMessagesContainer.getChildren().add(messageBlock);
    }
    
    /**
     * Add grouped message (same sender)
     */
    private void addGroupedGlobalMessage(String text) {
        HBox messageBlock = new HBox(16);
        messageBlock.setPadding(new Insets(0, 48, 0, 0));
        
        String timestamp = java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );
        
        Label timestampHover = new Label(timestamp);
        timestampHover.setMinWidth(40);
        timestampHover.setMaxWidth(40);
        timestampHover.setVisible(false);
        timestampHover.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-text-fill: " + DISCORD_TEXT_MUTED + "; " +
            "-fx-alignment: center-right; " +
            "-fx-padding: 0 5 0 0;"
        );
        timestampHover.setAlignment(Pos.CENTER_RIGHT);
        
        Label messageBody = new Label(text);
        messageBody.setWrapText(true);
        messageBody.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: " + DISCORD_TEXT_NORMAL + "; " +
            "-fx-line-spacing: 2;"
        );
        HBox.setHgrow(messageBody, Priority.ALWAYS);
        
        messageBlock.getChildren().addAll(timestampHover, messageBody);
        
        messageBlock.setOnMouseEntered(e -> {
            messageBlock.setStyle("-fx-background-color: " + DISCORD_BG_HOVER + "; -fx-background-radius: 4;");
            timestampHover.setVisible(true);
        });
        messageBlock.setOnMouseExited(e -> {
            messageBlock.setStyle("-fx-background-color: transparent;");
            timestampHover.setVisible(false);
        });
        
        HBox wrapper = new HBox();
        Region spacer = new Region();
        spacer.setMinWidth(56);
        wrapper.getChildren().addAll(spacer, messageBlock);
        HBox.setHgrow(messageBlock, Priority.ALWAYS);
        
        globalMessagesContainer.getChildren().add(wrapper);
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
        
        // Handle friend request notifications
        if (message.startsWith("FRIEND_REQUEST_RECEIVED ")) {
            handleFriendRequestReceived(message);
            return;
        }
        
        if (message.startsWith("FRIEND_REQUEST_SENT")) {
            Platform.runLater(() -> showTemporaryMessage("✅ Friend request sent!"));
            return;
        }
        
        if (message.startsWith("FRIEND_REQUEST_FAILED")) {
            // Parse the failure reason
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String reason = parts[2];
                Platform.runLater(() -> showTemporaryMessage("❌ " + reason));
            } else {
                Platform.runLater(() -> showTemporaryMessage("❌ Friend request failed"));
            }
            return;
        }
        
        if (message.startsWith("FRIEND_ACCEPTED ")) {
            handleFriendAccepted(message);
            return;
        }
        
        if (message.startsWith("FRIEND_REJECTED")) {
            Platform.runLater(() -> showTemporaryMessage("Friend request was rejected"));
            return;
        }
        
        if (message.startsWith("FRIENDSHIP_STATUS ")) {
            handleFriendshipStatus(message);
            return;
        }
        
        if (message.startsWith("PENDING_REQUEST ")) {
            handlePendingRequest(message);
            return;
        }
        
        if (message.startsWith("FRIENDS_LIST ")) {
            handleFriendsList(message);
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
                    
                    // ✅ Store in history (for both sent and received messages)
                    storePrivateMessage(otherUser, sender, text, timestamp, isOwnMessage);
                    
                    // Skip displaying if it's our own message (already displayed when sent)
                    if (isOwnMessage) {
                        return;
                    }
                    
                    // Add to DM contacts if not there
                    if (!dmContacts.contains(otherUser)) {
                        dmContacts.add(otherUser);
                    }
                    
                    // If private chat with this user is open, display it
                    if (currentPrivateChatUser != null && currentPrivateChatUser.equals(otherUser)) {
                        Platform.runLater(() -> addPrivateChatMessage(sender, text, false));
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
    }
    
    /**
     * Load stored messages when opening a private chat
     */
    private void loadStoredPrivateMessages(String username) {
        List<PrivateMessageData> history = privateMessageHistory.get(username);
        if (history != null && !history.isEmpty()) {
            for (PrivateMessageData msg : history) {
                addPrivateChatMessage(msg.sender, msg.text, msg.isOwnMessage);
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
                            // ✅ Skip our own username from the list
                            if (!userInfo.getUsername().equals(client.getUsername())) {
                                Label userLabel = createUserLabel(userInfo);
                                newUserLabels.put(userInfo.getUsername(), userLabel);
                                System.out.println("DEBUG: Added user: " + userInfo.getUsername());
                            }
                        } else {
                            System.err.println("DEBUG: Failed to parse user entry: " + part);
                        }
                    }
                }

                userLabels.clear();
                userLabels.putAll(newUserLabels);
                userListContainer.getChildren().setAll(newUserLabels.values());
                
                System.out.println("DEBUG: User list updated successfully: " + userLabels.size() + " users");
            } catch (Exception e) {
                System.err.println("Error updating user list: " + e.getMessage());
                e.printStackTrace();
            }
        });
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
        
        // Click to show user profile (with friend request option)
        final String username = userInfo.getUsername();
        container.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                // Single click - show profile
                System.out.println("Showing profile for: " + username);
                showUserProfile(username);
            } else if (e.getClickCount() == 2) {
                // Double click - open private chat (only if friends)
                if (isFriend(username)) {
                    System.out.println("Opening private chat with friend: " + username);
                    openPrivateChat(username);
                } else {
                    showTemporaryMessage("⚠️ You must be friends to send messages!");
                }
            }
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
                existingCall.getStage().toFront();
                showTemporaryMessage("📞 Un appel est déjà en cours - amenez la fenêtre en avant");
                System.out.println("⚠️ Tentative de démarrer un appel alors qu'un autre est actif");
            } else {
                showTemporaryMessage("⚠️ Un appel est en cours, attendez...");
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
                newCallWindow.disconnect();
                showTemporaryMessage("❌ Un autre appel est déjà en cours. Fermez-le d'abord.");
            }
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du démarrage de l'appel: " + e.getMessage());
            e.printStackTrace();
            showTemporaryMessage("❌ Erreur: " + e.getMessage());
        }
    }
    
    /**
     * Build the profile view panel
     */
    private VBox buildProfileView() {
        VBox profileContainer = new VBox(20);
        profileContainer.setPadding(new Insets(40));
        profileContainer.setAlignment(Pos.TOP_CENTER);
        profileContainer.setStyle("-fx-background-color: white;");
        
        // Profile header
        Label profileIcon = new Label("👤");
        profileIcon.setStyle("-fx-font-size: 80px;");
        
        Label usernameLabel = new Label(client.getUsername());
        usernameLabel.setStyle(
            "-fx-font-size: 28px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #2c3e50;");
        
        // Get user account info
        VBox infoBox = new VBox(15);
        infoBox.setPadding(new Insets(30));
        infoBox.setMaxWidth(500);
        infoBox.setStyle(
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 15px; " +
            "-fx-border-color: #e0e5ec; " +
            "-fx-border-radius: 15px; " +
            "-fx-border-width: 2px;");
        
        infoBox.getChildren().addAll(
            createInfoRow("🆔 Username", client.getUsername()),
            createInfoRow("🌐 Server", "Connected"),
            createInfoRow("📊 Status", "Online"),
            createInfoRow("💬 Messages", String.valueOf(messageCount)),
            createInfoRow("👥 Online Users", String.valueOf(userLabels.size()))
        );
        
        // Action buttons
        HBox actionButtons = new HBox(15);
        actionButtons.setAlignment(Pos.CENTER);
        
        Button editProfileBtn = new Button("✏️ Edit Profile");
        editProfileBtn.setStyle(
            "-fx-background-color: #667eea; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 20px; " +
            "-fx-padding: 10px 25px; " +
            "-fx-cursor: hand;");
        editProfileBtn.setOnAction(e -> {
            // TODO: Implement profile editing
            showTemporaryMessage("Profile editing coming soon!");
        });
        
        Button logoutBtn = new Button("🚪 Logout");
        logoutBtn.setStyle(
            "-fx-background-color: #e74c3c; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 20px; " +
            "-fx-padding: 10px 25px; " +
            "-fx-cursor: hand;");
        logoutBtn.setOnAction(e -> shutdown());
        
        actionButtons.getChildren().addAll(editProfileBtn, logoutBtn);
        
        profileContainer.getChildren().addAll(
            profileIcon,
            usernameLabel,
            infoBox,
            actionButtons
        );
        
        return profileContainer;
    }
    
    /**
     * Build the settings view panel
     */
    private VBox buildSettingsView() {
        VBox settingsContainer = new VBox(20);
        settingsContainer.setPadding(new Insets(40));
        settingsContainer.setAlignment(Pos.TOP_CENTER);
        settingsContainer.setStyle("-fx-background-color: white;");
        
        Label settingsIcon = new Label("⚙️");
        settingsIcon.setStyle("-fx-font-size: 60px;");
        
        Label titleLabel = new Label("Settings");
        titleLabel.setStyle(
            "-fx-font-size: 28px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #2c3e50;");
        
        VBox settingsBox = new VBox(15);
        settingsBox.setPadding(new Insets(30));
        settingsBox.setMaxWidth(600);
        settingsBox.setStyle(
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 15px; " +
            "-fx-border-color: #e0e5ec; " +
            "-fx-border-radius: 15px; " +
            "-fx-border-width: 2px;");
        
        settingsBox.getChildren().addAll(
            createSettingRow("🔔 Notifications", "Enabled"),
            createSettingRow("🎨 Theme", "Purple Gradient"),
            createSettingRow("📹 Video Quality", "High (720p)"),
            createSettingRow("🎤 Audio Quality", "High"),
            createSettingRow("💾 Message History", "Enabled (100 msgs)"),
            createSettingRow("🔒 RAID-1 Storage", "Active")
        );
        
        Label infoLabel = new Label("Settings customization coming in future updates");
        infoLabel.setStyle(
            "-fx-font-size: 12px; " +
            "-fx-text-fill: #7f8c8d; " +
            "-fx-font-style: italic;");
        
        settingsContainer.getChildren().addAll(
            settingsIcon,
            titleLabel,
            settingsBox,
            infoLabel
        );
        
        return settingsContainer;
    }
    
    /**
     * Create an info row for profile view
     */
    private HBox createInfoRow(String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label labelText = new Label(label);
        labelText.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea; " +
            "-fx-min-width: 150px;");
        
        Label valueText = new Label(value);
        valueText.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: #2c3e50;");
        
        row.getChildren().addAll(labelText, valueText);
        return row;
    }
    
    /**
     * Create a settings row
     */
    private HBox createSettingRow(String label, String value) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5));
        
        Label labelText = new Label(label);
        labelText.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-weight: 500; " +
            "-fx-text-fill: #2c3e50; " +
            "-fx-min-width: 200px;");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label valueText = new Label(value);
        valueText.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: #667eea; " +
            "-fx-font-weight: bold;");
        
        row.getChildren().addAll(labelText, spacer, valueText);
        return row;
    }
    
    /**
     * Show temporary message (replaces dialog)
     */
    private void showTemporaryMessage(String message) {
        Label tempLabel = new Label(message);
        tempLabel.setStyle(
            "-fx-background-color: #667eea; " +
            "-fx-text-fill: white; " +
            "-fx-padding: 15px 25px; " +
            "-fx-background-radius: 10px; " +
            "-fx-font-size: 14px;");
        
        VBox overlay = new VBox(tempLabel);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");
        
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
        new Thread(() -> {
            try {
                System.out.println("Shutting down chat window...");
                
                // Close video call if open
                if (videoCallWindow != null) {
                    try {
                        videoCallWindow.disconnect();
                    } catch (Exception e) {
                        System.err.println("Error closing video call: " + e.getMessage());
                    }
                }
                
                // Disconnect client
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
        }, "ShutdownThread").start();
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
                
                // ✅ CORRECTION: Only display global messages in global chat
                // Private messages should not appear in global chat history
                if (recipient.equals("all")) {
                    // Add Discord-style message for history (global chat only)
                    Platform.runLater(() -> addGlobalChatMessage(sender, text));
                    messageCount++;
                } else {
                    // Private message from history - store it but don't display in global chat
                    String timestamp = java.time.LocalTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                    
                    // Determine the other user
                    String otherUser = sender.equals(client.getUsername()) ? recipient : sender;
                    boolean isOwnMessage = sender.equals(client.getUsername());
                    
                    // Store in private message history
                    storePrivateMessage(otherUser, sender, text, timestamp, isOwnMessage);
                    
                    // Add to DM contacts if not there
                    if (!dmContacts.contains(otherUser)) {
                        dmContacts.add(otherUser);
                    }
                }
                
            } catch (Exception e) {
                System.err.println("Failed to parse history message: " + e.getMessage());
            }
        }
    }
    
    /**
     * Filter global chat user list based on search text
     */
    private void filterGlobalUserList(String searchText) {
        Platform.runLater(() -> {
            userListContainer.getChildren().clear();
            
            if (searchText.isEmpty()) {
                // Show all users when search is empty
                userListContainer.getChildren().setAll(userLabels.values());
            } else {
                // Show only matching users
                for (Map.Entry<String, Label> entry : userLabels.entrySet()) {
                    String username = entry.getKey();
                    if (username.toLowerCase().contains(searchText)) {
                        userListContainer.getChildren().add(entry.getValue());
                    }
                }
            }
        });
    }
    
    /**
     * Filter DM contact list based on search text
     */
    private void filterDmList(String searchText) {
        if (dmListContainer == null) return;
        
        Platform.runLater(() -> {
            // Store all DM entries temporarily
            List<javafx.scene.Node> allEntries = new ArrayList<>(dmListContainer.getChildren());
            dmListContainer.getChildren().clear();
            
            if (searchText.isEmpty()) {
                // Show all DM contacts when search is empty
                dmListContainer.getChildren().setAll(allEntries);
            } else {
                // Filter DM contacts
                for (javafx.scene.Node node : allEntries) {
                    if (node instanceof HBox) {
                        HBox entry = (HBox) node;
                        // Find the username label (second child after avatar)
                        if (entry.getChildren().size() >= 2) {
                            javafx.scene.Node secondChild = entry.getChildren().get(1);
                            if (secondChild instanceof Label) {
                                Label nameLabel = (Label) secondChild;
                                String username = nameLabel.getText();
                                if (username.toLowerCase().contains(searchText)) {
                                    dmListContainer.getChildren().add(entry);
                                }
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Handle incoming friend request
     */
    private void handleFriendRequestReceived(String message) {
        // Format: FRIEND_REQUEST_RECEIVED <requestId> <senderUsername>
        String[] parts = message.split(" ", 3);
        if (parts.length >= 3) {
            String requestId = parts[1];
            String senderUsername = parts[2];
            
            PendingFriendRequest request = new PendingFriendRequest(requestId, senderUsername);
            pendingFriendRequests.add(request);
            
            Platform.runLater(() -> {
                showFriendRequestNotification(senderUsername, requestId);
            });
        }
    }
    
    /**
     * Show friend request notification dialog
     */
    private void showFriendRequestNotification(String senderUsername, String requestId) {
        VBox notification = new VBox(15);
        notification.setAlignment(Pos.CENTER);
        notification.setPadding(new Insets(30));
        notification.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );
        notification.setMaxWidth(400);
        
        Label icon = new Label("👤");
        icon.setStyle("-fx-font-size: 48px;");
        
        Label title = new Label("Friend Request");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #667eea;");
        
        Label message = new Label(senderUsername + " wants to be your friend!");
        message.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");
        message.setWrapText(true);
        message.setAlignment(Pos.CENTER);
        
        HBox buttons = new HBox(15);
        buttons.setAlignment(Pos.CENTER);
        
        Button acceptBtn = new Button("✓ Accept");
        acceptBtn.setStyle(
            "-fx-background-color: #23a55a; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 10 25; " +
            "-fx-background-radius: 8; " +
            "-fx-cursor: hand;"
        );
        acceptBtn.setOnAction(e -> {
            client.acceptFriendRequest(requestId);
            pendingFriendRequests.removeIf(r -> r.requestId.equals(requestId));
            closeNotification();
        });
        
        Button rejectBtn = new Button("✕ Reject");
        rejectBtn.setStyle(
            "-fx-background-color: #ed4245; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 10 25; " +
            "-fx-background-radius: 8; " +
            "-fx-cursor: hand;"
        );
        rejectBtn.setOnAction(e -> {
            client.rejectFriendRequest(requestId);
            pendingFriendRequests.removeIf(r -> r.requestId.equals(requestId));
            closeNotification();
        });
        
        buttons.getChildren().addAll(acceptBtn, rejectBtn);
        notification.getChildren().addAll(icon, title, message, buttons);
        
        VBox overlay = new VBox(notification);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        
        BorderPane root = (BorderPane) stage.getScene().getRoot();
        root.setCenter(overlay);
    }
    
    private void closeNotification() {
        BorderPane root = (BorderPane) stage.getScene().getRoot();
        root.setCenter(mainContent);
    }
    
    /**
     * Handle friend request accepted
     */
    private void handleFriendAccepted(String message) {
        // Format: FRIEND_ACCEPTED <username>
        String[] parts = message.split(" ", 2);
        if (parts.length >= 2) {
            String friendUsername = parts[1];
            friendsList.add(friendUsername);
            friendshipCache.put(friendUsername, true);
            
            Platform.runLater(() -> {
                showTemporaryMessage("🎉 You are now friends with " + friendUsername + "!");
            });
        }
    }
    
    /**
     * Handle friendship status response
     */
    private void handleFriendshipStatus(String message) {
        // Format: FRIENDSHIP_STATUS <user1> <user2> <true/false>
        String[] parts = message.split(" ");
        if (parts.length >= 4) {
            String user2 = parts[2];
            boolean areFriends = Boolean.parseBoolean(parts[3]);
            friendshipCache.put(user2, areFriends);
        }
    }
    
    /**
     * Handle pending friend request
     */
    private void handlePendingRequest(String message) {
        // Format: PENDING_REQUEST <requestId> <senderUsername>
        String[] parts = message.split(" ", 3);
        if (parts.length >= 3) {
            String requestId = parts[1];
            String senderUsername = parts[2];
            PendingFriendRequest request = new PendingFriendRequest(requestId, senderUsername);
            if (!pendingFriendRequests.contains(request)) {
                pendingFriendRequests.add(request);
            }
        }
    }
    
    /**
     * Handle friends list response
     */
    private void handleFriendsList(String message) {
        // Format: FRIENDS_LIST <username> <friend1> <friend2> ...
        String[] parts = message.split(" ");
        if (parts.length >= 2) {
            friendsList.clear();
            for (int i = 2; i < parts.length; i++) {
                String friend = parts[i];
                friendsList.add(friend);
                friendshipCache.put(friend, true);
            }
            System.out.println("Updated friends list: " + friendsList);
        }
    }
    
    /**
     * Check if user is a friend
     */
    private boolean isFriend(String username) {
        return friendshipCache.getOrDefault(username, false);
    }
    
    /**
     * Show user profile dialog with friend request button
     */
    private void showUserProfile(String username) {
        // ✅ Ne pas afficher le profil si c'est nous-même
        if (username.equals(client.getUsername())) {
            showTemporaryMessage("👤 This is your own profile!");
            return;
        }
        
        // Check friendship status first
        client.checkFriendship(username);
        
        // Wait a bit for response
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            Platform.runLater(() -> {
                VBox profileDialog = new VBox(20);
                profileDialog.setAlignment(Pos.CENTER);
                profileDialog.setPadding(new Insets(30));
                profileDialog.setStyle(
                    "-fx-background-color: white; " +
                    "-fx-background-radius: 15px; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
                );
                profileDialog.setMaxWidth(350);
                
                Label avatar = new Label(getAvatarEmoji(username));
                avatar.setStyle("-fx-font-size: 64px;");
                
                Label nameLabel = new Label(username);
                nameLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333;");
                
                HBox buttons = new HBox(10);
                buttons.setAlignment(Pos.CENTER);
                
                if (isFriend(username)) {
                    // Already friends - show message button
                    Button messageBtn = new Button("💬 Send Message");
                    messageBtn.setStyle(
                        "-fx-background-color: #5865f2; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 10 20; " +
                        "-fx-background-radius: 8; " +
                        "-fx-cursor: hand;"
                    );
                    messageBtn.setOnAction(e -> {
                        openPrivateChat(username);
                        closeNotification();
                    });
                    buttons.getChildren().add(messageBtn);
                } else {
                    // Not friends - show friend request button
                    Button friendRequestBtn = new Button("➕ Add Friend");
                    friendRequestBtn.setStyle(
                        "-fx-background-color: #23a55a; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 10 20; " +
                        "-fx-background-radius: 8; " +
                        "-fx-cursor: hand;"
                    );
                    friendRequestBtn.setOnAction(e -> {
                        client.sendFriendRequest(username);
                        closeNotification();
                        showTemporaryMessage("Friend request sent to " + username);
                    });
                    buttons.getChildren().add(friendRequestBtn);
                }
                
                Button closeBtn = new Button("Close");
                closeBtn.setStyle(
                    "-fx-background-color: #4f545c; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 10 20; " +
                    "-fx-background-radius: 8; " +
                    "-fx-cursor: hand;"
                );
                closeBtn.setOnAction(e -> closeNotification());
                buttons.getChildren().add(closeBtn);
                
                profileDialog.getChildren().addAll(avatar, nameLabel, buttons);
                
                VBox overlay = new VBox(profileDialog);
                overlay.setAlignment(Pos.CENTER);
                overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
                
                BorderPane root = (BorderPane) stage.getScene().getRoot();
                root.setCenter(overlay);
            });
        }).start();
    }

    public void show() {
        stage.show();
    }
}
