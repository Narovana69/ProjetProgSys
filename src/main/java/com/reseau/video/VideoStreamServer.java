package com.reseau.video;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.opencv.core.Core;

/**
 * VideoStreamServer - Handles video streaming for NEXO video calls
 * Receives video frames from clients and broadcasts to all other clients in the same room
 */
public class VideoStreamServer {

    public static final int DEFAULT_PORT = 5000;

    private final int port;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool;

    private final Map<Integer, DataOutputStream> clientOutputs = new ConcurrentHashMap<>();
    private final Map<Integer, String> clientUsernames = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    public VideoStreamServer(int port) {
        this.port = port;
        this.clientPool = Executors.newCachedThreadPool();
    }

    public void startAsync() {
        Thread t = new Thread(this::runServer, "video-stream-server");
        t.setDaemon(true);
        t.start();
        System.out.println("Video Stream Server started on port " + port);
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        clientPool.shutdownNow();
        System.out.println("Video Stream Server stopped");
    }

    private void runServer() {
        running = true;
        loadOpenCvNative();

        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            System.out.println("Video server listening on port " + port);
            
            while (running) {
                Socket socket = ss.accept();
                System.out.println("New video client connected: " + socket.getInetAddress());
                clientPool.submit(() -> handleClient(socket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Video server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        final int clientId = nextClientId.getAndIncrement();

        try (Socket s = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 65536));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 65536))) {

            // Optimize socket for low latency
            s.setTcpNoDelay(true);
            s.setSendBufferSize(131072);
            s.setReceiveBufferSize(131072);

            // Read username from client
            int usernameLen = in.readInt();
            byte[] usernameBytes = new byte[usernameLen];
            in.readFully(usernameBytes);
            String username = new String(usernameBytes, "UTF-8");
            
            clientOutputs.put(clientId, out);
            clientUsernames.put(clientId, username);
            
            // Send client ID back
            out.writeInt(clientId);
            out.flush();
            
            System.out.println("Video client registered: " + username + " (ID: " + clientId + ")");
            broadcastClientCount();

            while (running && !s.isClosed()) {
                int len;
                try {
                    len = in.readInt();
                } catch (IOException e) {
                    break;
                }

                if (len <= 0 || len > 50_000_000) {
                    break;
                }

                byte[] bytes = new byte[len];
                in.readFully(bytes);

                broadcastFrame(clientId, bytes);
            }
        } catch (IOException ignored) {
        } finally {
            String username = clientUsernames.remove(clientId);
            clientOutputs.remove(clientId);
            System.out.println("Video client disconnected: " + username + " (ID: " + clientId + ")");
            broadcastClientCount();
        }
    }

    private void broadcastClientCount() {
        int count = clientOutputs.size();
        for (DataOutputStream out : clientOutputs.values()) {
            synchronized (out) {
                try {
                    out.writeInt(0); // senderId 0 = system message
                    out.writeInt(4);
                    out.writeInt(count);
                    out.flush();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void broadcastFrame(int senderId, byte[] bytes) {
        for (Map.Entry<Integer, DataOutputStream> entry : clientOutputs.entrySet()) {
            if (entry.getKey() == senderId) {
                continue;
            }

            DataOutputStream out = entry.getValue();
            synchronized (out) {
                try {
                    out.writeInt(senderId);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                    out.flush();
                } catch (IOException e) {
                    // ignore; client cleanup happens on its own read/write failure
                }
            }
        }
    }

    private void loadOpenCvNative() {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Throwable t) {
            try {
                System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            } catch (Throwable t2) {
                System.err.println("Warning: Could not load OpenCV native library");
            }
        }
    }
    
    public int getConnectedClientsCount() {
        return clientOutputs.size();
    }
    
    public boolean isRunning() {
        return running;
    }
}
