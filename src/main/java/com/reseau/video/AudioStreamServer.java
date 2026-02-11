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

/**
 * AudioStreamServer - Handles audio streaming for NEXO video calls
 * Receives audio frames from clients and broadcasts to all other clients
 */
public class AudioStreamServer {

    public static final int DEFAULT_PORT = 6000;

    private final int port;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final ExecutorService clientPool;

    private final Map<Integer, DataOutputStream> clientOutputs = new ConcurrentHashMap<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    public AudioStreamServer(int port) {
        this.port = port;
        this.clientPool = Executors.newCachedThreadPool();
    }

    public void startAsync() {
        Thread t = new Thread(this::runServer, "audio-stream-server");
        t.setDaemon(true);
        t.start();
        System.out.println("Audio Stream Server started on port " + port);
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
        System.out.println("Audio Stream Server stopped");
    }

    private void runServer() {
        running = true;

        try (ServerSocket ss = new ServerSocket(port)) {
            this.serverSocket = ss;
            System.out.println("Audio server listening on port " + port);
            
            while (running) {
                Socket socket = ss.accept();
                System.out.println("New audio client connected: " + socket.getInetAddress());
                clientPool.submit(() -> handleClient(socket));
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Audio server error: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket socket) {
        final int clientId = nextClientId.getAndIncrement();

        try (Socket s = socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 16384));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 16384))) {

            // Optimize socket for low latency audio
            s.setTcpNoDelay(true);
            s.setSendBufferSize(65536);
            s.setReceiveBufferSize(65536);

            clientOutputs.put(clientId, out);

            // Handshake: inform client of its assigned id.
            out.writeInt(clientId);
            out.flush();
            
            System.out.println("Audio client registered (ID: " + clientId + ")");

            while (running && !s.isClosed()) {
                int len;
                try {
                    len = in.readInt();
                } catch (IOException e) {
                    break;
                }

                if (len <= 0 || len > 2_000_000) {
                    break;
                }

                byte[] bytes = new byte[len];
                in.readFully(bytes);

                broadcastFrame(clientId, bytes);
            }
        } catch (IOException ignored) {
        } finally {
            clientOutputs.remove(clientId);
            System.out.println("Audio client disconnected (ID: " + clientId + ")");
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
                    // ignore
                }
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
