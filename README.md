# 💬 NEXO - Secure TCP Chat Application with Authentication

A modern, real-time chat application built with Java and JavaFX, featuring user authentication, presence tracking, video calling, and secure password storage.

## ✨ Features

### Core Messaging
- 🚀 **Real-time messaging** with TCP/IP protocol
- 💬 **Message buffering** prevents race conditions on connection
- 📝 **Persistent user accounts** with encrypted credentials
- 💾 **Message history** automatically loaded on login (last 100 messages)
- 🔄 **RAID-1 storage** with dual-disk mirroring for fault tolerance
- ⚡ **Async disk writes** every 5 seconds for performance
- 🛡️ **Automatic recovery** from disk failures

### Authentication & Security 🔐
- 🔒 **Secure login system** with username and password
- 🆕 **User registration** with profile data (first name, last name)
- 🔐 **SHA-256 password hashing** with salt for security
- 👤 **User profiles** accessible from chat window
- 🔑 **Master password protection** for admin access (hidden with Ctrl+Shift+A)
- 💾 **Server address memory** saves last connected server
- ✅ **Username uniqueness verification** during registration
- 🔄 **Legacy CONNECT support** for backward compatibility

### Presence & Communication
- 👥 **User presence system** with heartbeat monitoring
- 🟢 **Status indicators**: Online, Inactive, Offline
- 💻 **Device detection**: Windows, Mac, Linux icons
- 🌐 **WiFi networking**: Connect with friends on same network
- 🔄 **Auto-refresh**: User list updates in real-time

### Video & Audio
- 📹 **Video calling** with multi-party support (2x2 grid)
- 🎤 **Audio streaming** with real-time mixing
- 📷 **OpenCV integration** for video capture
- 🔊 **JavaSound API** for audio capture

### User Interface
- 🎨 **Modern UI**: Beautiful gradient purple design (#667eea → #764ba2)
- 📱 **Multi-client**: Support unlimited simultaneous connections
- 🪟 **Profile button**: View user information from chat
- 📹 **Video call button**: Launch video conference from chat

## 🎯 Quick Start

### 📋 Prerequisites
- Java 17 or higher
- Apache Maven 3.6+
- JavaFX 17 (included in dependencies)
- OpenCV 4.7.0 (included in dependencies)

### 🖥️ First Time Setup (Registration)

**Terminal 1 - Start Server:**
```bash
cd /home/randylam/Documents/GitHub/Projet_MrNainaV1.1
mvn exec:java -Dexec.mainClass="com.reseau.server.Server"
```
Server will start on ports:
- **8080**: Chat and authentication
- **5000**: Video streaming
- **6000**: Audio streaming

**Terminal 2 - Start Client:**
```bash
./start-client.sh
# or
mvn javafx:run
```

**Initial Setup:**
1. Click "**Don't have an account? Sign up**" link
2. Fill in registration form:
   - First Name: `Alice`
   - Last Name: `Smith`
   - Username: `alice`
   - Password: `********` (min 6 characters)
   - Confirm Password: `********`
   - Server Address: `localhost:8080`
3. Click "**Register**"
4. On success, you'll be returned to login screen
5. Login with your username and password

**Second Client (Terminal 3):**
```bash
./start-client.sh
```
- Register as `Bob Johnson` with username `bob`
- Login and start chatting with Alice

### 🔒 Authentication System

**User Registration:**
- Creates new account with profile data
- Passwords are hashed using SHA-256 with salt
- Username uniqueness is verified
- Server address is saved for convenience

**Login:**
- Enter username and password
- Server validates credentials
- On success, joins chat room and **loads message history**
- Last server address is remembered
- View last 100 messages with original timestamps

**Profile Access:**
- Click 👤 button in chat window
- View your profile information
- Shows username, first name, last name, creation date

**Admin Access (Hidden):**
- Press **Ctrl+Shift+A** on login screen
- Enter master password (set during first server start)
- Access reserved for system administrator only

### 📹 Video Calling

- Click 📹 button in chat window
- Video call window opens with 2x2 participant grid
- Controls: Start/Stop Video, Start/Stop Audio
- OpenCV captures video from webcam
- Audio captured from default microphone

## 🔐 Security Features

### Password Storage
- All passwords hashed with SHA-256
- Unique salt per user prevents rainbow table attacks
- Stored format: `<salt>:<hash>` in .nexo_users.dat
- Master password separately encrypted in .nexo_master.key

### Data Persistence
- **User Accounts**: `.nexo_users.dat` (Java serialization)
- **Master Password**: `.nexo_master.key` (encrypted)
- **Client Config**: `.nexo_config.properties` (last server address)
- **Message Archive**: `.nexo_messages_primary.dat` (RAID-1 primary)
- **Message Mirror**: `.nexo_messages_mirror.dat` (RAID-1 backup)

### RAID-1 Message Storage
- **Dual-disk mirroring**: Every message written to 2 files simultaneously
- **Fault tolerance**: Survives single disk failure
- **Automatic recovery**: Failed disk restored from mirror on restart
- **Async writes**: Batched every 5 seconds for performance
- **Capacity**: Stores up to 10,000 messages (auto-prunes oldest)
- **Format**: Java serialization with timestamps

### Network Security
- TCP sockets with SO_KEEPALIVE
- Heartbeat monitoring (5-second interval)
- Automatic timeout detection (15 seconds)
- Clean connection handling

## 📡 Connecting Over WiFi

### Step 1: Find Server IP Address
```bash
hostname -I | awk '{print $1}'
```
Example output: `10.159.59.164`

### Step 2: Start Server on Host Computer
```bash
mvn exec:java -Dexec.mainClass="com.reseau.server.Server"
```

### Step 3: Connect from Friend's Computer
```bash
mvn javafx:run
```
- Server: `10.159.59.164` (use the IP from Step 1)
- Username: `YourName`
- Click "Connect ⚡"

**⚠️ Important**: Both devices must be on the **same WiFi network**!

### 🔥 Firewall Setup (If Connection Fails)

**Linux:**
```bash
sudo ufw allow 8080/tcp
sudo ufw reload
```

**Windows:**
- Windows Defender Firewall → Advanced Settings → Inbound Rules
- New Rule → Port → TCP → 8080 → Allow

**Mac:**
- System Preferences → Security & Privacy → Firewall
- Add Java to allowed apps

📖 **See [WIFI_CONNECTION_GUIDE.md](WIFI_CONNECTION_GUIDE.md) for detailed instructions**

## 🏗️ Architecture

```
NEXO/
├── Server (Port 8080)
│   ├── Accepts TCP connections
│   ├── Manages user presence
│   └── Broadcasts messages
│
├── Client (JavaFX)
│   ├── LoginWindow - Connection UI
│   ├── ChatWindow - Main interface
│   └── Heartbeat sender (5s interval)
│
└── Protocol
    ├── CONNECT <username>
    ├── MESSAGE <from> <to> <text>
    ├── HEARTBEAT <user> <device> <ip>
    ├── USER_LIST <user1|device1|ip1|status1> ...
    ├── REFRESH_USERS
    └── DISCONNECT
```

## 📦 Project Structure

```
src/main/java/com/reseau/
├── server/
│   ├── Server.java           # Main TCP server
│   ├── ClientHandler.java    # Per-client thread handler
│   └── PresenceService.java  # Heartbeat monitoring
│
├── client/
│   ├── ClientApp.java         # JavaFX entry point
│   ├── LoginWindow.java       # Connection UI
│   ├── ChatWindow.java        # Main chat interface
│   └── Client.java            # TCP client & networking
│
└── common/
    └── UserInfo.java          # User presence data model
```

## 🛠️ Development Phases

### ✅ Phase 1: TCP Messaging System
- Client-server architecture with TCP sockets
- Modern JavaFX UI (gradient purple design)
- Basic MESSAGE/CONNECT/DISCONNECT protocol

### ✅ Phase 2: User Presence & Heartbeat
- Heartbeat protocol (5s interval)
- PresenceService with 15s timeout
- User list with status indicators (🟢🟡⚫)
- Device icons (🪟🍎🐧💻)
- Manual refresh button

### 🔨 Phase 3: File Transfer (Coming Soon)
- FILE_REQUEST/FILE_ACCEPT/FILE_DATA protocol
- Progress bars
- Multi-file support

### 🔨 Phase 4: Group Chat (Planned)
- Group creation and management
- Channel/room support
- Broadcast to groups

## 🎨 UI Features

- **Modern Gradient Design**: Purple (#667eea → #764ba2)
- **Smooth Animations**: Hover effects and transitions
- **Rounded Corners**: 10px border radius
- **Shadow Effects**: Depth and modern look
- **Emoji Integration**: Status and device indicators
- **Responsive Layout**: BorderPane with dynamic sizing

## 🐛 Troubleshooting

### Connection Refused
- ✅ Verify server is running
- ✅ Check correct IP address (not 127.0.0.1 for remote!)
- ✅ Ensure port 8080 is open

### User List Not Updating
- Click the 🔄 **Refresh** button
- Wait 5 seconds for next heartbeat
- Check server logs for HEARTBEAT messages

### Exit Button Errors
- **✅ FIXED**: Now properly disconnects and exits
- Stops heartbeat thread
- Closes all network resources
- Calls Platform.exit() and System.exit(0)

### Can't Connect Over WiFi
- Verify both devices on **same network**
- Check firewall allows port 8080
- Ping server: `ping <server-ip>`
- Try disabling firewall temporarily

## 📊 Network Protocol

### Message Format
All messages are text-based with space delimiters:

```
CONNECT <username>
→ CONNECTED

MESSAGE <sender> <recipient> <text>

HEARTBEAT <username> <device> <ip>

USER_LIST <user1|device1|ip1|status1> <user2|device2|ip2|status2>

REFRESH_USERS
→ USER_LIST ...

DISCONNECT
```

### Status Types
- `ONLINE` (🟢): Active, receiving heartbeats
- `INACTIVE` (🟡): No heartbeat for 15+ seconds
- `OFFLINE` (⚫): Disconnected

## 🔐 Security Notes

**⚠️ Current implementation is for LOCAL networks only!**

- ❌ No encryption (plain text)
- ❌ No authentication (no passwords)
- ❌ No message persistence
- ✅ Suitable for: Home, education, testing
- ❌ NOT for: Public networks, sensitive data

For production, add:
- TLS/SSL encryption
- User authentication
- Rate limiting
- End-to-end encryption
- Message history database

## 📝 Testing Guide

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for:
- Detailed testing scenarios
- Expected server logs
- Multi-client testing steps
- Heartbeat verification

## 🌐 WiFi Connection Guide

See [WIFI_CONNECTION_GUIDE.md](WIFI_CONNECTION_GUIDE.md) for:
- Step-by-step WiFi setup
- Firewall configuration
- Network topology examples
- Troubleshooting tips
- Quick reference commands

## 🤝 Contributing

This is an educational project. Feel free to:
- Report issues
- Suggest features
- Submit pull requests
- Use for learning purposes

## 📄 License

Educational project - free to use and modify.

## 🎓 Learning Objectives

This project demonstrates:
- **Network Programming**: TCP/IP sockets, client-server architecture
- **Concurrent Programming**: Thread pools, ExecutorService
- **JavaFX UI**: Modern interface design, Platform threading
- **Protocol Design**: Text-based messaging protocol
- **Presence System**: Heartbeat monitoring, timeout detection
- **Real-time Communication**: Message broadcasting, user synchronization

## 📞 Your Current Setup

**Your Server IP**: `10.159.59.164`

**Quick Commands:**
```bash
# Find your IP
hostname -I | awk '{print $1}'

# Start server
mvn exec:java -Dexec.mainClass="com.reseau.server.Server"

# Start client
mvn javafx:run

# Compile
mvn compile

# Clean and compile
mvn clean compile
```

---

Built with ❤️ for learning network programming and JavaFX
