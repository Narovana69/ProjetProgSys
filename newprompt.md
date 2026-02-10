# Simplified Communication App: Networking & Resilience with JavaFX UI

## Core Goal

Build an educational communication application similar to Discord using **Java 17+** and **JavaFX**, focusing on networking protocols (90%) and UI interaction (10%). Emphasis on **resource efficiency** and **optimized implementations**.

**Minimum Java Version:** Java 17

**Name :** NEXO
---

## I. Key Functional Modules

### 1. Messaging System with Fault-Tolerant Storage (RAID-1)

**UI:** Chat window, text input field, send button  
**Network:** TCP sockets with SO_KEEPALIVE, TCP_NODELAY optimization  
**Storage:** File-based RAID-1 mirroring (2 disks minimum)

#### Implementation (Optimized)
- **Write:** Save message to 2 disk files simultaneously (message ID as filename)
- **Read:** Primary disk first, fallback to secondary if unavailable
- **Failure Test:** Delete one disk file, verify recovery from remaining copy
- **Classes:** `MessageStorage`, `DiskManager` (optional)

**Tradeoff:** Simple + reliable vs. doubled storage  
**Note:** RAID-5 striping discussed conceptually only

---

### 2. User Presence Monitoring (Heartbeat System)

**UI:** User list with online/offline status indicators, device type icons, IP addresses  
**Network:** TCP heartbeat every 5s, `NetworkInterface` for IP detection  
**Threading:** `ConcurrentHashMap` for thread-safe presence registry

#### Implementation (Optimized)
- **Client:** Background thread sends `HEARTBEAT <username> <device> <ip>` every 5s
- **Server:** Update `lastSeen` timestamp, mark offline if no heartbeat for 15s
- **Metadata:** Username, OS (`System.getProperty("os.name")`), IP address, status
- **States:** Online → Inactive (no heartbeat) → Disconnected (explicit)
- **Classes:** `PresenceService`, `ClientHandler`

---

### 3. File Sharing System (Request-Accept-Transfer)

**UI:** File picker dialog, accept/reject dialog, progress bar  
**Network:** TCP control channel, chunked binary transfer (64KB chunks)  
**Protocol:** Request → Accept/Reject → Data chunks → Complete

#### Implementation (Optimized)
1. **Request:** `FILE_REQUEST <filename> <size> <recipient>`
2. **Response:** `FILE_ACCEPT` or `FILE_REJECT`
3. **Transfer:** `FILE_DATA <chunk_number> <base64_data>` (64KB chunks)
4. **Complete:** `FILE_COMPLETE` → verify file size
5. **Storage:** Fixed directory (`./received_files/`)
6. **Cleanup:** Delete partial files on failure/cancellation
7. **Classes:** `FileSharingService`, `FileTransferRequest`, `FileShareDialog`

**Error Handling:** Request timeout (30s), transfer interruption, size mismatch

---

### 4. Screen Sharing System (JPEG Streaming)

**UI:** Share screen button, viewer window with ImageView, fullscreen toggle  
**Network:** TCP relay, JPEG-encoded frames  
**Performance:** 10 FPS (100ms delay), quality-controlled compression

#### Implementation (Optimized)
1. **Capture:** `Robot.createScreenCapture()` → `BufferedImage`
2. **Encode:** JPEG compression via `ImageIO.write()` (quality: 0.7)
3. **Stream:** Base64-encoded JPEG via `SCREEN_FRAME <base64_data>`
4. **Relay:** Server forwards frames to all viewers
5. **Display:** Decode → update `ImageView` via `Platform.runLater()`
6. **Controls:** Start/stop, frame rate indicator, status updates
7. **Classes:** `ScreenShareService`, `ScreenBroadcaster`, `ScreenViewerWindow`

**Optimization:** Frame rate throttling (10 FPS), viewer count limit (5 max), automatic cleanup on disconnect

---

## II. Architecture & Principles

- **Client-Server:** Clear separation, TCP sockets (port 8080)
- **Concurrency:** Thread pool for clients (ExecutorService), thread-safe collections
- **Protocols:** Text-based with clear delimiters
  - `MESSAGE <sender> <recipient> <text>`
  - `HEARTBEAT <username> <device> <ip>`
  - `FILE_REQUEST <filename> <size> <recipient>`
  - `SCREEN_START`, `SCREEN_FRAME`, `SCREEN_STOP`
- **Services:** Modular design (`MessageStorage`, `PresenceService`, `FileSharingService`, `ScreenShareService`)
- **JavaFX:** Functional UI with thread-safe updates (`Platform.runLater()`)
- **Documentation:** Inline comments explaining networking decisions

---

## III. Out of Scope

- Voice/video calls
- Public servers/channels
- Embedded file uploads
- End-to-end encryption
- Elaborate UI styling (focus on functionality)

---

## IV. Expected Outcomes

- **Functional JavaFX application** with clean, intuitive interface
- **Deep understanding of:**
  - TCP socket programming and optimization
  - Concurrency patterns (thread pools, synchronization)
  - RAID-1 fault tolerance
  - Real-time streaming tradeoffs
  - Resource-efficient implementations
- **Well-documented codebase** with design rationale
- **Test coverage:** RAID-1 storage, presence monitoring, file/screen sharing

---

## V. Development Plan

1. **Setup:** JavaFX Maven project (Java 17+), organize client/server structure
2. **Basic Communication:** TCP socket messaging, test with network tools
3. **JavaFX UI:** Login window, main chat window (clean, modern design)
4. **Messaging:** Real-time message display without persistence
5. **Presence:** Heartbeat system with user list UI
6. **RAID-1 Storage:** Message persistence with fault tolerance
7. **File Sharing:** Full protocol with UI integration
8. **Screen Sharing:** Capture, stream, view with performance monitoring
9. **Error Handling:** Comprehensive error checks and logging
10. **Testing:** Network simulation (disconnects, latency), load testing
11. **Documentation:** README, inline comments, architecture diagrams

---

## VI. User Interface Requirements

### Design Principles
- **Modern, clean appearance** with professional look
- **Intuitive layout** with clear visual hierarchy
- **Responsive design** adapting to window resizing
- **Visual feedback** for all user actions (loading states, confirmations)
- **Resource-efficient rendering** (no unnecessary redraws)

### Login Window (`src/main/java/com/reseau/client/LoginWindow.java`)
- Username input field
- Server address input (default: localhost:8080)
- Connect button with loading state
- Status messages (errors, connecting)
- Clean centered layout

### Main Chat Window (`src/main/java/com/reseau/client/ChatWindow.java`)
- **Title:** "Discords - [Username]"
- **Left Panel:** User list with:
  - Status dots (green=online, yellow=inactive, gray=offline)
  - Device type icons
  - Hover tooltips showing IP and device info
- **Center Panel:** Message display with:
  - Auto-scrolling
  - Timestamp, sender, content for each message
  - Message grouping by sender
- **Bottom Panel:** 
  - Text input field (multi-line support)
  - Send button (Enter key shortcut)
  - File share button
  - Screen share button
- **Clean color scheme:** Light background, subtle borders, readable fonts

### File Transfer Dialog (`src/main/java/com/reseau/client/FileShareDialog.java`)
- **Sender:** Progress bar, cancel button, transfer speed indicator
- **Receiver:** Accept/reject dialog with filename, size, sender info
- **Notifications:** Success/failure messages

### Screen Viewer Window (`src/main/java/com/reseau/client/ScreenViewerWindow.java`)
- Separate window showing shared screen
- Controls: Stop viewing, fullscreen toggle
- Status bar: Frame rate, resolution, broadcaster name
- Smooth frame updates without flicker

### UI Best Practices
- Thread-safe updates using `Platform.runLater()`
- No blocking operations on JavaFX Application Thread
- Proper resource cleanup on window close
- Keyboard shortcuts (Enter=send, Esc=close dialogs)

---

## VII. Project Tracking

### TodoList (`TodoList.md`)
Track implementation progress with:
- Phase numbers and descriptions
- File paths for modified/created files
- Completion status (✅/⏳/❌)
- Dependencies between phases

### Example Structure:
```markdown
## Phase 1: Project Setup ✅
- `pom.xml` - Maven dependencies
- `src/main/java/com/reseau/server/Server.java`
- `src/main/java/com/reseau/client/Client.java`

## Phase 2: Basic Messaging ✅
- `src/main/java/com/reseau/server/ClientHandler.java`
- `src/main/java/com/reseau/protocol/Message.java`
```

---

## VIII. Performance & Optimization Guidelines

### Resource Management
- **Thread Pool:** Fixed size (10-20 threads), prevent thread explosion
- **Memory:** Limit message history (1000 messages max), cleanup old entries
- **Network:** Buffer sizes (8KB), connection pooling, timeout management
- **File Transfer:** Chunk size (64KB), stream processing (no full file in memory)
- **Screen Sharing:** Frame rate throttling (10 FPS), quality compression (0.7), viewer limit (5)

### Monitoring
- Log network statistics (bytes sent/received, latency)
- Track active threads and memory usage
- Alert on resource threshold violations
- Graceful degradation under load

---

## IX. README File (`README.md`)

Create comprehensive documentation including:
- Project overview and learning objectives
- Prerequisites (Java 17+, JavaFX, Maven)
- Build instructions (`mvn clean install`)
- Run instructions (server: `mvn exec:java`, client: `mvn javafx:run`)
- Architecture overview with diagrams
- Protocol specifications
- Testing procedures
- Known limitations and future improvements
- Troubleshooting guide

### Unamed additional features
- bigger UI
- additional login/sign-in
- scan for users on the same network
- add friend feature: keeps the original for one user-> keep it low storage cost.
- we can choose the network when connected, like wifi or even lan
- it is a network project so everything works on lan, we can scan for users on that same lan(means: have the app and is on the network).
---