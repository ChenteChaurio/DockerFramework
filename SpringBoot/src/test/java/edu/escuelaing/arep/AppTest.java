package edu.escuelaing.arep;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

public class AppTest {

    @Test
    public void testHelloControllerIndex() {
        assertEquals("Greetings from Spring Boot!", HelloController.index());
    }

    @Test
    public void testHelloControllerPi() {
        assertTrue(HelloController.webMethodPi().startsWith("Pi= "));
    }

    @Test
    public void testHelloControllerHello() {
        assertEquals("Hello World", HelloController.webMethodHello());
    }

    @Test
    public void testGreetingControllerWithName() {
        GreetingController controller = new GreetingController();
        assertEquals("Hola Juan", controller.greeting("Juan"));
    }

    @Test
    public void testGreetingControllerDefaultName() {
        GreetingController controller = new GreetingController();
        assertEquals("Hola World", controller.greeting("World"));
    }

    @Test
    public void testRestControllerAnnotationPresent() {
        assertTrue(HelloController.class.isAnnotationPresent(RestController.class));
        assertTrue(GreetingController.class.isAnnotationPresent(RestController.class));
    }

    @Test
    public void testGetMappingAnnotationValue() throws NoSuchMethodException {
        Method m = HelloController.class.getDeclaredMethod("index");
        assertTrue(m.isAnnotationPresent(GetMapping.class));
        assertEquals("/", m.getAnnotation(GetMapping.class).value());
    }

    @Test
    public void testGetMappingOnGreetingController() throws NoSuchMethodException {
        Method m = GreetingController.class.getDeclaredMethod("greeting", String.class);
        assertTrue(m.isAnnotationPresent(GetMapping.class));
        assertEquals("/greeting", m.getAnnotation(GetMapping.class).value());
    }

    @Test
    public void testRequestParamAnnotation() throws NoSuchMethodException {
        Method m = GreetingController.class.getDeclaredMethod("greeting", String.class);
        Parameter p = m.getParameters()[0];
        assertTrue(p.isAnnotationPresent(RequestParam.class));
        RequestParam rp = p.getAnnotation(RequestParam.class);
        assertEquals("name", rp.value());
        assertEquals("World", rp.defaultValue());
    }

    @Test
    public void testHttpRequestGetValues() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "Carlos");
        HttpRequest req = new HttpRequest("/greeting", params);
        assertEquals("Carlos", req.getValues("name"));
        assertEquals("", req.getValues("missing"));
    }

    @Test
    public void testConcurrentRequests() throws Exception {
        // Arrange
        HttpServer.get("/slow", (req, res) -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "OK";
        });

        Thread serverThread = new Thread(HttpServer::start, "test-server-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        waitForPortOpen("localhost", 8080, 2000);

        int requests = 10;
        ExecutorService exec = Executors.newFixedThreadPool(requests);
        long start = System.currentTimeMillis();

        try {
            Future<String>[] futures = new Future[requests];
            for (int i = 0; i < requests; i++) {
                futures[i] = exec.submit(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return httpGet("/slow");
                    }
                });
            }

            for (int i = 0; i < requests; i++) {
                assertTrue(futures[i].get(2, TimeUnit.SECONDS).contains("OK"));
            }
        } finally {
            exec.shutdownNow();
        }

        long elapsed = System.currentTimeMillis() - start;

        // If it were strictly sequential, we'd expect ~requests*150ms.
        // With concurrency, it should be significantly less than that.
        assertTrue(elapsed < 1200, "Expected concurrent handling, but took " + elapsed + "ms");

        // Cleanup
        HttpServer.stop();
        waitForPortClosed("localhost", 8080, 2000);
    }

    @Test
    public void testGracefulShutdownReleasesPort() throws Exception {
        Thread serverThread = new Thread(HttpServer::start, "test-server-thread-2");
        serverThread.setDaemon(true);
        serverThread.start();

        waitForPortOpen("localhost", 8080, 2000);

        HttpServer.stop();

        waitForPortClosed("localhost", 8080, 2000);

        // Verify we can bind again (port released)
        try (ServerSocket ss = new ServerSocket(8080)) {
            assertTrue(ss.isBound());
        }
    }

    private static String httpGet(String path) throws IOException {
        try (Socket socket = new Socket("localhost", 8080)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.print("GET " + path + " HTTP/1.1\r\n");
            out.print("Host: localhost\r\n");
            out.print("Connection: close\r\n");
            out.print("\r\n");
            out.flush();

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private static void waitForPortOpen(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket(host, port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        fail("Port " + port + " did not open in time");
    }

    private static void waitForPortClosed(String host, int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket ignored = new Socket(host, port)) {
                Thread.sleep(50);
            } catch (ConnectException e) {
                return;
            } catch (IOException e) {
                return;
            }
        }
        fail("Port " + port + " did not close in time");
    }
}
