package com.reseau.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Stage;

/**
 * VideoCallWindow - Video conference window for NEXO
 * Handles video capture, streaming and display
 */
public class VideoCallWindow {

    private Stage stage;
    private String username;
    private String serverHost;
    private int videoPort;
    private int audioPort;

    // UI Components
    private TilePane remoteGrid;
    private ImageView localView;
    private Label statusLabel;
    private ToggleButton muteMicButton;
    private ToggleButton muteCameraButton;
    private ToggleButton muteSpeakerButton;
    private BorderPane root;

    // Remote tiles management
    private static class RemoteTileState {
        final ImageView view;
        final AtomicReference<Image> latest = new AtomicReference<>();
        final AtomicBoolean scheduled = new AtomicBoolean(false);
        volatile long lastSeenNanos;

        RemoteTileState(ImageView view) {
            this.view = view;
        }
    }

    private final Map<Integer, RemoteTileState> remoteTiles = new ConcurrentHashMap<>();

    private final ScheduledExecutorService tileReaper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "remote-tile-reaper");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> tileReaperTask;
    private static final long REMOTE_TILE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(4);

    // Video streaming
    private volatile boolean running;
    private Socket socket;
    private Thread readerThread;
    private Thread senderThread;
    private int myClientId;

    // Audio streaming
    private Socket audioSocket;
    private Thread audioSessionThread;
    private Thread audioCaptureThread;
    private Thread audioMixerThread;
    private volatile boolean audioRunning;
    private final AtomicBoolean muteMic = new AtomicBoolean(false);
    private final AtomicBoolean muteCamera = new AtomicBoolean(false);
    private final AtomicBoolean muteSpeaker = new AtomicBoolean(false);
    private final Map<Integer, ArrayBlockingQueue<byte[]>> audioBuffers = new ConcurrentHashMap<>();

    // Audio constants — CD-quality mono (44.1 kHz, 16-bit, mono)
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private static final int AUDIO_FRAME_MS = 40;
    private static final int AUDIO_SAMPLES_PER_FRAME = (AUDIO_SAMPLE_RATE * AUDIO_FRAME_MS) / 1000;
    private static final int AUDIO_BYTES_PER_FRAME = AUDIO_SAMPLES_PER_FRAME * 2; // 3528 bytes
    // Noise gate threshold — samples below this amplitude are silenced (anti-feedback)
    private static final short NOISE_GATE_THRESHOLD = 200;
    // Noise gate fade length (in samples) for smooth transitions
    private static final int NOISE_GATE_FADE_SAMPLES = 64;
    // High-pass filter cutoff (removes low-freq rumble below ~100 Hz)
    private static final float HP_FILTER_ALPHA = 0.993f; // ~100Hz @ 44.1kHz

    private static final int VIDEO_FRAME_DELAY_MS = 50;
    
    // Callback when window is closed
    private Runnable onWindowClosed;
    
    // Guard against double disconnect
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    public VideoCallWindow(String username, String serverHost, int videoPort, int audioPort) {
        this.username = username;
        this.serverHost = serverHost;
        this.videoPort = videoPort;
        this.audioPort = audioPort;
        this.stage = new Stage();
        setupUI();
    }

    private void setupUI() {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");

        // Top bar
        HBox topBar = new HBox(15);
        topBar.setPadding(new Insets(15, 20, 15, 20));
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle(
            "-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);"
        );

        Label iconLabel = new Label("📹");
        iconLabel.setStyle("-fx-font-size: 24px;");

        Label titleLabel = new Label("NEXO Video Call");
        titleLabel.setStyle(
            "-fx-font-size: 20px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: white;"
        );

        statusLabel = new Label("Connecting...");
        statusLabel.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-text-fill: rgba(255,255,255,0.8); " +
            "-fx-padding: 5px 15px;"
        );

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        topBar.getChildren().addAll(iconLabel, titleLabel, spacer, statusLabel);

        // Remote videos grid
        remoteGrid = new TilePane();
        remoteGrid.setHgap(10);
        remoteGrid.setVgap(10);
        remoteGrid.setPadding(new Insets(15));
        remoteGrid.setStyle("-fx-background-color: #1a1a2e;");
        remoteGrid.setPrefColumns(2);

        // Local video preview (bottom right corner)
        localView = new ImageView();
        localView.setFitWidth(200);
        localView.setFitHeight(150);
        localView.setPreserveRatio(true);
        localView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0, 0, 0);");

        StackPane localViewContainer = new StackPane(localView);
        localViewContainer.setStyle(
            "-fx-background-color: #2a2a4e; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #667eea; " +
            "-fx-border-radius: 10; " +
            "-fx-border-width: 2;"
        );
        localViewContainer.setPadding(new Insets(5));
        localViewContainer.setMaxWidth(210);
        localViewContainer.setMaxHeight(160);

        // Wrap remote grid with local view overlay
        StackPane centerPane = new StackPane();
        centerPane.getChildren().addAll(remoteGrid, localViewContainer);
        StackPane.setAlignment(localViewContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localViewContainer, new Insets(0, 20, 20, 0));

        // Bottom controls
        HBox controlBar = new HBox(20);
        controlBar.setPadding(new Insets(15, 20, 15, 20));
        controlBar.setAlignment(Pos.CENTER);
        controlBar.setStyle("-fx-background-color: #16213e;");

        muteMicButton = createToggleButton("🎤", "Mute Mic");
        muteMicButton.setOnAction(e -> {
            muteMic.set(muteMicButton.isSelected());
            muteMicButton.setText(muteMic.get() ? "🔇 Mic Off" : "🎤 Mic On");
        });

        muteCameraButton = createToggleButton("📷", "Camera Off");
        muteCameraButton.setOnAction(e -> {
            muteCamera.set(muteCameraButton.isSelected());
            muteCameraButton.setText(muteCamera.get() ? "📷 Cam Off" : "📹 Cam On");
        });

        muteSpeakerButton = createToggleButton("🔊", "Speaker Off");
        muteSpeakerButton.setOnAction(e -> {
            muteSpeaker.set(muteSpeakerButton.isSelected());
            muteSpeakerButton.setText(muteSpeaker.get() ? "🔇 Sound Off" : "🔊 Sound On");
        });

        Button hangupButton = new Button("❌ End Call");
        hangupButton.setStyle(
            "-fx-background-color: #e74c3c; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 25px; " +
            "-fx-padding: 10px 25px; " +
            "-fx-cursor: hand;"
        );
        hangupButton.setOnMouseEntered(e -> hangupButton.setStyle(
            "-fx-background-color: #c0392b; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 25px; " +
            "-fx-padding: 10px 25px; " +
            "-fx-cursor: hand;"
        ));
        hangupButton.setOnMouseExited(e -> hangupButton.setStyle(
            "-fx-background-color: #e74c3c; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 25px; " +
            "-fx-padding: 10px 25px; " +
            "-fx-cursor: hand;"
        ));
        hangupButton.setOnAction(e -> disconnect());

        controlBar.getChildren().addAll(muteMicButton, muteCameraButton, muteSpeakerButton, hangupButton);

        root.setTop(topBar);
        root.setCenter(centerPane);
        root.setBottom(controlBar);

        // Add resize listener
        root.widthProperty().addListener((obs, oldV, newV) -> updateRemoteGridLayout());
        root.heightProperty().addListener((obs, oldV, newV) -> updateRemoteGridLayout());

        Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);
        stage.setTitle("NEXO Video Call - " + username);
        stage.setMinWidth(600);
        stage.setMinHeight(500);

        stage.setOnCloseRequest(e -> {
            disconnect();
            // onWindowClosed is already called inside disconnect(), no need to call again
        });
    }

    private ToggleButton createToggleButton(String emoji, String text) {
        ToggleButton btn = new ToggleButton(emoji + " " + text);
        btn.setStyle(
            "-fx-background-color: #2a2a4e; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 13px; " +
            "-fx-background-radius: 25px; " +
            "-fx-padding: 10px 20px; " +
            "-fx-cursor: hand;"
        );
        btn.setOnMouseEntered(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(
                    "-fx-background-color: #3a3a6e; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 13px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-cursor: hand;"
                );
            }
        });
        btn.setOnMouseExited(e -> {
            if (!btn.isSelected()) {
                btn.setStyle(
                    "-fx-background-color: #2a2a4e; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 13px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-cursor: hand;"
                );
            }
        });
        btn.selectedProperty().addListener((obs, old, newVal) -> {
            if (newVal) {
                btn.setStyle(
                    "-fx-background-color: #e74c3c; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 13px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-cursor: hand;"
                );
            } else {
                btn.setStyle(
                    "-fx-background-color: #2a2a4e; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 13px; " +
                    "-fx-background-radius: 25px; " +
                    "-fx-padding: 10px 20px; " +
                    "-fx-cursor: hand;"
                );
            }
        });
        return btn;
    }

    public void connect() {
        running = true;
        startTileReaper();

        muteMic.set(false);
        muteCamera.set(false);
        muteSpeaker.set(false);

        readerThread = new Thread(() -> runVideoReader(serverHost, videoPort), "video-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        startAudio(serverHost, audioPort);
    }

    private void runVideoReader(String host, int port) {
        Platform.runLater(() -> statusLabel.setText("Connecting to " + host + ":" + port + " ..."));

        try (Socket s = new Socket(host, port);
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 65536));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 65536))) {

            this.socket = s;
            s.setTcpNoDelay(true);
            s.setSendBufferSize(131072);
            s.setReceiveBufferSize(131072);
            
            // Send username
            byte[] usernameBytes = username.getBytes("UTF-8");
            out.writeInt(usernameBytes.length);
            out.write(usernameBytes);
            out.flush();
            
            // Receive client ID
            myClientId = in.readInt();
            
            Platform.runLater(() -> statusLabel.setText("Connected (ID: " + myClientId + ")"));
            
            // Notify VideoCallManager that we are connected
            VideoCallManager.getInstance().callConnected();

            senderThread = new Thread(() -> runVideoSender(out), "video-sender");
            senderThread.setDaemon(true);
            senderThread.start();

            while (running) {
                int senderId;
                int len;
                try {
                    senderId = in.readInt();
                    len = in.readInt();
                } catch (IOException e) {
                    break;
                }

                if (len <= 0 || len > 50_000_000) {
                    break;
                }

                byte[] bytes = new byte[len];
                in.readFully(bytes);

                if (senderId == 0 && len == 4) {
                    int count = new DataInputStream(new ByteArrayInputStream(bytes)).readInt();
                    Platform.runLater(() -> statusLabel.setText("Connected - " + count + " participants"));
                } else {
                    Image image = new Image(new ByteArrayInputStream(bytes));
                    scheduleRemoteTileUpdate(senderId, image);
                }
            }
        } catch (IOException e) {
            Platform.runLater(() -> statusLabel.setText("Connection failed: " + e.getMessage()));
            VideoCallManager.getInstance().callFailed(e.getMessage());
        } finally {
            // Use disconnect() which has the guard against double-call
            disconnect();
        }
    }

    private void runVideoSender(DataOutputStream out) {
        loadOpenCvNative();

        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            Platform.runLater(() -> statusLabel.setText("Camera open failed"));
            return;
        }

        // Lower camera resolution for less latency
        capture.set(3, 320);  // CV_CAP_PROP_FRAME_WIDTH
        capture.set(4, 240);  // CV_CAP_PROP_FRAME_HEIGHT

        Mat frame = new Mat();
        Mat flipped = new Mat();
        MatOfByte buffer = new MatOfByte();
        // JPEG quality 40% — prioritize low latency over image quality
        MatOfInt jpegParams = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 40);

        AtomicReference<byte[]> latestFrame = new AtomicReference<>();
        Thread networkThread = new Thread(() -> runVideoNetworkSender(out, latestFrame), "video-network-sender");
        networkThread.setDaemon(true);
        networkThread.start();

        // Local preview rate limiter
        long lastLocalPreview = 0;
        final long LOCAL_PREVIEW_INTERVAL_NS = 50_000_000L; // 50ms = 20fps for preview

        try {
            while (running && socket != null && !socket.isClosed()) {
                boolean ok = capture.read(frame);
                if (!ok || frame.empty()) {
                    continue;
                }

                // Flip horizontally (mirror) so the local user sees themselves correctly
                Core.flip(frame, flipped, 1);

                buffer.release();
                buffer = new MatOfByte();

                boolean encoded = Imgcodecs.imencode(".jpg", flipped, buffer, jpegParams);
                if (!encoded) {
                    continue;
                }

                byte[] bytes = buffer.toArray();
                
                if (!muteCamera.get()) {
                    latestFrame.set(bytes);
                }

                // Show local preview at reduced rate
                long now = System.nanoTime();
                if (localView != null && (now - lastLocalPreview) >= LOCAL_PREVIEW_INTERVAL_NS) {
                    lastLocalPreview = now;
                    Image localImage = new Image(new ByteArrayInputStream(bytes));
                    Platform.runLater(() -> localView.setImage(localImage));
                }

                try {
                    Thread.sleep(VIDEO_FRAME_DELAY_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } finally {
            flipped.release();
            capture.release();
            jpegParams.release();
            networkThread.interrupt();
        }
    }

    private void runVideoNetworkSender(DataOutputStream out, AtomicReference<byte[]> latestFrame) {
        while (running && socket != null && !socket.isClosed()) {
            byte[] bytes = latestFrame.getAndSet(null);
            if (bytes == null) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            synchronized (out) {
                try {
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    private void scheduleRemoteTileUpdate(int senderId, Image image) {
        RemoteTileState state = remoteTiles.computeIfAbsent(senderId, id -> {
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            view.setSmooth(true);
            view.setCache(true);
            view.setStyle(
                "-fx-background-color: #2a2a4e; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 0);"
            );

            RemoteTileState created = new RemoteTileState(view);
            created.lastSeenNanos = System.nanoTime();
            Platform.runLater(() -> {
                if (remoteGrid != null) {
                    remoteGrid.getChildren().add(view);
                    updateRemoteGridLayout();
                }
            });
            return created;
        });

        state.lastSeenNanos = System.nanoTime();
        state.latest.set(image);
        if (!state.scheduled.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            try {
                Image img = state.latest.getAndSet(null);
                if (img != null) {
                    state.view.setImage(img);
                }
            } finally {
                state.scheduled.set(false);
            }
        });
    }

    private void updateRemoteGridLayout() {
        if (root == null || remoteGrid == null) {
            return;
        }

        int count = remoteGrid.getChildren().size();
        if (count <= 0) {
            remoteGrid.setPrefColumns(1);
            return;
        }

        double availableW = Math.max(0, root.getWidth() - 60);
        double minTileW = 280.0;
        int columns = (int) Math.floor((availableW + remoteGrid.getHgap()) / (minTileW + remoteGrid.getHgap()));
        columns = Math.max(1, Math.min(columns, count));

        remoteGrid.setPrefColumns(columns);

        double tileW = (availableW - ((columns - 1) * remoteGrid.getHgap())) / columns;
        if (tileW < 1) {
            tileW = minTileW;
        }
        double tileH = tileW * 9.0 / 16.0;

        remoteGrid.setPrefTileWidth(tileW);
        remoteGrid.setPrefTileHeight(tileH);

        for (RemoteTileState state : remoteTiles.values()) {
            state.view.setFitWidth(tileW);
            state.view.setFitHeight(tileH);
        }
    }

    private void loadOpenCvNative() {
        try {
            nu.pattern.OpenCV.loadLocally();
            return;
        } catch (Throwable ignored) {
        }

        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    public void disconnect() {
        // Guard against double disconnect
        if (!disconnected.compareAndSet(false, true)) {
            System.out.println("⚠️ disconnect() already called, skipping");
            return;
        }
        
        disconnectInternal();
        
        // Notify the VideoCallManager that the call is done
        Runnable cb = onWindowClosed;
        if (cb != null) {
            onWindowClosed = null;
            Platform.runLater(cb);
        }
        
        Platform.runLater(() -> {
            statusLabel.setText("Disconnected");
            stage.close();
        });
    }

    private void disconnectInternal() {
        running = false;
        audioRunning = false;

        stopTileReaper();

        Platform.runLater(() -> {
            if (remoteGrid != null) {
                remoteGrid.getChildren().clear();
            }
            remoteTiles.clear();
        });

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        } finally {
            socket = null;
        }

        try {
            if (audioSocket != null) {
                audioSocket.close();
            }
        } catch (IOException ignored) {
        } finally {
            audioSocket = null;
        }

        if (readerThread != null && Thread.currentThread() != readerThread) {
            readerThread.interrupt();
        }

        if (senderThread != null && Thread.currentThread() != senderThread) {
            senderThread.interrupt();
        }

        if (audioCaptureThread != null && Thread.currentThread() != audioCaptureThread) {
            audioCaptureThread.interrupt();
        }

        if (audioSessionThread != null && Thread.currentThread() != audioSessionThread) {
            audioSessionThread.interrupt();
        }

        if (audioMixerThread != null && Thread.currentThread() != audioMixerThread) {
            audioMixerThread.interrupt();
        }

        audioBuffers.clear();
    }

    private void startTileReaper() {
        stopTileReaper();
        tileReaperTask = tileReaper.scheduleAtFixedRate(this::purgeStaleRemoteTiles, 1, 1, TimeUnit.SECONDS);
    }

    private void stopTileReaper() {
        if (tileReaperTask != null) {
            tileReaperTask.cancel(false);
            tileReaperTask = null;
        }
    }

    private void purgeStaleRemoteTiles() {
        if (!running) {
            return;
        }
        long now = System.nanoTime();
        for (Map.Entry<Integer, RemoteTileState> e : remoteTiles.entrySet()) {
            RemoteTileState state = e.getValue();
            if (state == null) {
                continue;
            }
            if (now - state.lastSeenNanos <= REMOTE_TILE_TIMEOUT_NANOS) {
                continue;
            }

            Integer id = e.getKey();
            remoteTiles.remove(id);
            Platform.runLater(() -> {
                if (remoteGrid != null) {
                    remoteGrid.getChildren().remove(state.view);
                    updateRemoteGridLayout();
                }
            });
        }
    }

    // Audio methods
    private void startAudio(String host, int port) {
        audioRunning = true;

        audioSessionThread = new Thread(() -> runAudioSession(host, port), "audio-session");
        audioSessionThread.setDaemon(true);
        audioSessionThread.start();
    }

    private AudioFormat audioFormat() {
        return new AudioFormat(AUDIO_SAMPLE_RATE, 16, 1, true, false);
    }

    private void runAudioSession(String host, int port) {
        try (Socket s = new Socket(host, port);
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 16384));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 16384))) {

            this.audioSocket = s;
            s.setTcpNoDelay(true);
            s.setSendBufferSize(65536);
            s.setReceiveBufferSize(65536);

            final int myAudioId = in.readInt();

            audioMixerThread = new Thread(this::runAudioMixer, "audio-mixer");
            audioMixerThread.setDaemon(true);
            audioMixerThread.start();

            audioCaptureThread = new Thread(() -> runAudioCapture(out), "audio-capture");
            audioCaptureThread.setDaemon(true);
            audioCaptureThread.start();

            while (audioRunning && !s.isClosed()) {
                int senderId;
                int len;
                try {
                    senderId = in.readInt();
                    len = in.readInt();
                } catch (IOException e) {
                    break;
                }

                if (len <= 0 || len > 2_000_000) {
                    break;
                }

                byte[] bytes = new byte[len];
                in.readFully(bytes);

                if (senderId == myAudioId) {
                    continue;
                }

                // No attenuation needed — noise gate on sender side prevents feedback
                ArrayBlockingQueue<byte[]> q = audioBuffers.computeIfAbsent(senderId, k -> new ArrayBlockingQueue<>(50));
                q.offer(bytes);
            }
        } catch (IOException ignored) {
        } finally {
            audioRunning = false;
        }
    }

    private void runAudioCapture(DataOutputStream out) {
        AudioFormat fmt = audioFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);

        TargetDataLine mic;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(fmt, AUDIO_BYTES_PER_FRAME * 6);
            mic.start();
        } catch (LineUnavailableException e) {
            System.err.println("Microphone unavailable: " + e.getMessage());
            return;
        }

        byte[] buf = new byte[AUDIO_BYTES_PER_FRAME];
        // High-pass filter state (removes low-frequency rumble/hum)
        float hpPrev = 0f;
        float hpOut = 0f;

        try {
            while (audioRunning && audioSocket != null && !audioSocket.isClosed()) {
                int read = 0;
                while (read < buf.length) {
                    int r = mic.read(buf, read, buf.length - read);
                    if (r <= 0) {
                        break;
                    }
                    read += r;
                }

                if (read <= 0) {
                    continue;
                }

                if (read < buf.length) {
                    for (int i = read; i < buf.length; i++) {
                        buf[i] = 0;
                    }
                }

                if (muteMic.get()) {
                    for (int i = 0; i < buf.length; i++) {
                        buf[i] = 0;
                    }
                } else {
                    // 1. Apply high-pass filter to remove low-frequency rumble/hum
                    int samples = buf.length / 2;
                    for (int i = 0; i < samples; i++) {
                        int lo = buf[i * 2] & 0xFF;
                        int hi = buf[i * 2 + 1];
                        float sample = (float) ((short) ((hi << 8) | lo));

                        hpOut = HP_FILTER_ALPHA * (hpOut + sample - hpPrev);
                        hpPrev = sample;

                        int v = Math.round(hpOut);
                        if (v > Short.MAX_VALUE) v = Short.MAX_VALUE;
                        if (v < Short.MIN_VALUE) v = Short.MIN_VALUE;
                        buf[i * 2] = (byte) (v & 0xFF);
                        buf[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
                    }

                    // 2. Apply smooth noise gate (with fade in/out)
                    applyNoiseGate(buf);
                }

                synchronized (out) {
                    out.writeInt(buf.length);
                    out.write(buf, 0, buf.length);
                    out.flush();
                }
            }
        } catch (IOException ignored) {
        } finally {
            mic.stop();
            mic.close();
        }
    }

    /**
     * Smooth noise gate: if the RMS amplitude of the frame is below
     * the threshold, fade the frame to silence instead of cutting abruptly.
     * This prevents audible "click" artifacts at gate boundaries.
     */
    private boolean noiseGateOpen = true;

    private void applyNoiseGate(byte[] pcm) {
        int sampleCount = pcm.length / 2;

        // Compute RMS amplitude (more accurate than average absolute)
        long sumSquares = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int lo = pcm[i] & 0xFF;
            int hi = pcm[i + 1];
            short s = (short) ((hi << 8) | lo);
            sumSquares += (long) s * s;
        }
        double rms = Math.sqrt((double) sumSquares / Math.max(1, sampleCount));

        boolean shouldBeOpen = rms >= NOISE_GATE_THRESHOLD;

        if (!shouldBeOpen && !noiseGateOpen) {
            // Gate was closed and stays closed — silence
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = 0;
            }
            return;
        }

        if (shouldBeOpen && noiseGateOpen) {
            // Gate was open and stays open — pass through
            return;
        }

        // Transition: apply fade in or fade out
        int fadeSamples = Math.min(NOISE_GATE_FADE_SAMPLES, sampleCount);

        if (!shouldBeOpen && noiseGateOpen) {
            // Closing: fade out at end of frame
            noiseGateOpen = false;
            int fadeStart = sampleCount - fadeSamples;
            for (int i = fadeStart; i < sampleCount; i++) {
                float factor = 1.0f - (float)(i - fadeStart) / fadeSamples;
                int lo = pcm[i * 2] & 0xFF;
                int hi = pcm[i * 2 + 1];
                short s = (short) ((hi << 8) | lo);
                int v = Math.round(s * factor);
                pcm[i * 2] = (byte) (v & 0xFF);
                pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }
        } else {
            // Opening: fade in at start of frame
            noiseGateOpen = true;
            for (int i = 0; i < fadeSamples; i++) {
                float factor = (float) i / fadeSamples;
                int lo = pcm[i * 2] & 0xFF;
                int hi = pcm[i * 2 + 1];
                short s = (short) ((hi << 8) | lo);
                int v = Math.round(s * factor);
                pcm[i * 2] = (byte) (v & 0xFF);
                pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
            }
        }
    }

    private void runAudioMixer() {
        AudioFormat fmt = audioFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

        SourceDataLine speakers;
        try {
            // Buffer for ~120ms of audio at 44.1kHz (3 frames * 3528 bytes)
            int bufferSize = AUDIO_BYTES_PER_FRAME * 3;
            speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(fmt, bufferSize);
            speakers.start();
        } catch (LineUnavailableException e) {
            System.err.println("Speakers unavailable: " + e.getMessage());
            return;
        }

        try {
            FloatControl gain = (FloatControl) speakers.getControl(FloatControl.Type.MASTER_GAIN);
            float targetDb = -1.5f; // Slightly louder than before (-3dB) for clarity
            float clamped = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), targetDb));
            gain.setValue(clamped);
        } catch (IllegalArgumentException ignored) {
        }

        byte[] mixedBytes = new byte[AUDIO_BYTES_PER_FRAME];
        int[] mix = new int[AUDIO_SAMPLES_PER_FRAME];

        try {
            while (audioRunning && audioSocket != null && !audioSocket.isClosed()) {
                for (int i = 0; i < mix.length; i++) {
                    mix[i] = 0;
                }

                boolean any = false;
                for (ArrayBlockingQueue<byte[]> q : audioBuffers.values()) {
                    byte[] frame = q.poll();
                    if (frame == null) {
                        continue;
                    }

                    if (frame.length != AUDIO_BYTES_PER_FRAME) {
                        continue;
                    }

                    any = true;

                    int samples = Math.min(mix.length, frame.length / 2);
                    for (int i = 0; i < samples; i++) {
                        int lo = frame[i * 2] & 0xFF;
                        int hi = frame[i * 2 + 1];
                        short s = (short) ((hi << 8) | lo);
                        mix[i] += s;
                    }
                }

                if (!any) {
                    // No audio data available — write silence and yield briefly
                    for (int i = 0; i < mixedBytes.length; i++) {
                        mixedBytes[i] = 0;
                    }
                    speakers.write(mixedBytes, 0, mixedBytes.length);
                    try {
                        Thread.sleep(AUDIO_FRAME_MS / 2);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                } else {
                    // Soft-clipping: use tanh-based soft limiter instead of hard clamp
                    // This produces smoother distortion when multiple streams mix
                    for (int i = 0; i < mix.length; i++) {
                        int v = mix[i];
                        if (v > Short.MAX_VALUE || v < Short.MIN_VALUE) {
                            // Soft clip using tanh curve
                            double normalized = (double) v / Short.MAX_VALUE;
                            v = (int) (Math.tanh(normalized) * Short.MAX_VALUE);
                        }
                        mixedBytes[i * 2] = (byte) (v & 0xFF);
                        mixedBytes[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
                    }
                }

                if (muteSpeaker.get()) {
                    for (int i = 0; i < mixedBytes.length; i++) {
                        mixedBytes[i] = 0;
                    }
                }

                speakers.write(mixedBytes, 0, mixedBytes.length);
            }
        } finally {
            speakers.drain();
            speakers.stop();
            speakers.close();
        }
    }

    /**
     * Show the video call window and start the connection
     * This is the main entry point - call this once to start the call
     */
    public void show() {
        stage.show();
        connect();
    }

    public Stage getStage() {
        return stage;
    }
    
    public void setOnWindowClosed(Runnable callback) {
        this.onWindowClosed = callback;
    }
}
