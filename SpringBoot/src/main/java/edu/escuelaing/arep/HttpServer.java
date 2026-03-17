package edu.escuelaing.arep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HttpServer {
    static Map<String, WebMethod> endPoints = new ConcurrentHashMap<>();
    static volatile String staticFilesPath = "";

    // Keep request handling fast; enable only when debugging locally.
    private static final boolean DEBUG_LOG = false;

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_SHUTDOWN_TIMEOUT_SECONDS = 5;
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private static volatile ServerSocket serverSocket;
    private static volatile ExecutorService workerPool;

    public static void main(String[] args) throws IOException, URISyntaxException {
        startServer(DEFAULT_PORT);
    }

    public static void get(String path, WebMethod wm) {
        endPoints.put(path, wm);
    }

    public static void staticfiles(String path) {
        staticFilesPath = path;
    }

    public static void start() {
        try {
            startServer(DEFAULT_PORT);
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    /**
     * Stops the server gracefully: stops accepting new connections, closes the
     * server socket and
     * waits for in-flight requests to finish.
     */
    public static void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        System.out.println("Stopping server...");

        ServerSocket ss = serverSocket;
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
            } catch (IOException e) {
                // ignore
            }
        }

        ExecutorService pool = workerPool;
        if (pool != null) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(DEFAULT_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        workerPool = null;
        serverSocket = null;
        System.out.println("Server stopped.");
    }

    private static void startServer(int port) throws IOException {
        if (!running.compareAndSet(false, true)) {
            System.out.println("Server is already running.");
            return;
        }
        serverSocket = new ServerSocket(port);

        int nThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        workerPool = Executors.newFixedThreadPool(nThreads, new ThreadFactory() {
            private int idx = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "http-worker-" + (++idx));
                t.setDaemon(false);
                return t;
            }
        });

        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // must be safe to call multiple times
                stop();
            }, "httpserver-shutdown-hook"));
        }

        System.out.println("HTTP server listening on port " + port + " with " + nThreads + " workers");

        if (DEBUG_LOG) {
            System.out.println("Listo para recibir ...");
        }

        while (running.get()) {
            try {
                final Socket clientSocket = serverSocket.accept();
                workerPool.execute(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                // Usually thrown when serverSocket.close() is called during shutdown.
                if (running.get()) {
                    System.err.println("Socket error while accepting: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                Socket socket = clientSocket;
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String inputLine;
            boolean firstline = true;
            String reqPath = "";
            Map<String, String> reqParams = new HashMap<>();

            while ((inputLine = in.readLine()) != null) {
                if (DEBUG_LOG) {
                    System.out.println("Received: " + inputLine);
                }
                if (firstline) {
                    String[] firstLineTokens = inputLine.split(" ");
                    if (firstLineTokens.length < 2) {
                        break;
                    }
                    String srturi = firstLineTokens[1];
                    URI requri = new URI(srturi);
                    reqPath = requri.getPath();
                    String query = requri.getQuery();
                    reqParams = queryParams(query);
                    if (DEBUG_LOG) {
                        System.out.println("Path: " + reqPath);
                        System.out.println("Query params: " + reqParams);
                    }
                    firstline = false;
                }
                if (!in.ready()) {
                    break;
                }
            }

            HttpRequest req = new HttpRequest(reqPath, reqParams);
            HttpResponse res = new HttpResponse();

            WebMethod wm = endPoints.get(reqPath);
            if (wm != null) {
                out.println("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/html\r\n"
                        + "\r\n"
                        + "<!DOCTYPE html>"
                        + "<html>"
                        + "<head>"
                        + "<meta charset=\"UTF-8\">"
                        + "<title>Response</title>"
                        + "</head>"
                        + "<body>"
                        + wm.execute(req, res)
                        + "</body>"
                        + "</html>");
            } else if (isBinaryFile(reqPath)) {
                byte[] fileBytes = readStaticFileBytes(reqPath);
                if (fileBytes != null) {
                    String header = "HTTP/1.1 200 OK\r\nContent-Type: "
                            + getContentType(reqPath) + "\r\n\r\n";
                    OutputStream rawOut = socket.getOutputStream();
                    rawOut.write(header.getBytes(StandardCharsets.UTF_8));
                    rawOut.write(fileBytes);
                    rawOut.flush();
                } else {
                    out.println("HTTP/1.1 404 Not Found\r\n"
                            + "Content-Type: text/html\r\n\r\n"
                            + "<!DOCTYPE html><html>"
                            + "<head><meta charset=\"UTF-8\"><title>404 Not Found</title></head>"
                            + "<body><h1>404 Not Found</h1></body></html>");
                }
            } else {
                String fileContent = readStaticFile(reqPath);
                if (fileContent != null) {
                    String contentType = getContentType(reqPath);
                    out.println("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: " + contentType + "\r\n"
                            + "\r\n"
                            + fileContent);
                } else {
                    out.println("HTTP/1.1 404 Not Found\r\n"
                            + "Content-Type: text/html\r\n"
                            + "\r\n"
                            + "<!DOCTYPE html>"
                            + "<html>"
                            + "<head><meta charset=\"UTF-8\"><title>404 Not Found</title></head>"
                            + "<body>"
                            + "<h1>404 Not Found</h1>"
                            + "<p>The requested resource was not found on this server.</p>"
                            + "</body>"
                            + "</html>");
                }
            }
        } catch (URISyntaxException e) {
            System.err.println("Bad request URI: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error handling client: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error handling client: " + e.getMessage());
        }
    }

    private static Map<String, String> queryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] tuples = query.split("&");
        for (String tuple : tuples) {
            String[] keyAndValue = tuple.split("=");
            if (keyAndValue.length == 2) {
                params.put(keyAndValue[0], keyAndValue[1]);
            } else if (keyAndValue.length == 1) {
                params.put(keyAndValue[0], "");
            }
        }
        return params;
    }

    private static String getContentType(String file) {
        if (file.endsWith(".html"))
            return "text/html";
        if (file.endsWith(".css"))
            return "text/css";
        if (file.endsWith(".js"))
            return "application/javascript";
        if (file.endsWith(".png"))
            return "image/png";
        if (file.endsWith(".jpg") || file.endsWith(".jpeg"))
            return "image/jpeg";
        return "text/plain";
    }

    private static String readStaticFile(String filePath) {
        try {
            String fullPath = staticFilesPath + filePath;
            InputStream inputStream = HttpServer.class.getResourceAsStream(fullPath);
            if (inputStream == null) {
                return null;
            }
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath);
            return null;
        }
    }

    private static boolean isBinaryFile(String path) {
        return path.endsWith(".png") || path.endsWith(".jpg")
                || path.endsWith(".jpeg") || path.endsWith(".gif")
                || path.endsWith(".ico");
    }

    private static byte[] readStaticFileBytes(String filePath) {
        try {
            String fullPath = staticFilesPath + filePath;
            InputStream inputStream = HttpServer.class.getResourceAsStream(fullPath);
            if (inputStream == null) {
                return null;
            }
            try (inputStream) {
                return inputStream.readAllBytes();
            }
        } catch (IOException e) {
            System.err.println("Error reading binary file: " + filePath);
            return null;
        }
    }
}
