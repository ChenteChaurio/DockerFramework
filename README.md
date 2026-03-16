# MicroSpringBoot - IoC Web Framework in Java (Concurrent + Graceful Shutdown)

Lightweight HTTP framework built from scratch in Java 17, inspired by Spring Boot.

### What it does

- Serves **static files** (HTML/CSS/JS/images) from `src/main/resources/webroot`.
- Supports **lambda endpoints**: `HttpServer.get("/path", (req,res) -> ...)`.
- Supports **annotation-based controllers** (`@RestController`, `@GetMapping`, `@RequestParam`) discovered at runtime.
- Handles **concurrent requests** using a fixed thread pool.
- Supports **graceful shutdown** using a JVM **Shutdown Hook** (`Runtime.getRuntime().addShutdownHook(...)`).

## Architecture

The framework has two usage modes:

### 1. Lambda-style API (`App.java`)

Register endpoints directly using lambdas, similar to Express.js:

```java
staticfiles("/webroot");
get("/App/hello", (req, resp) -> "Hello " + req.getValues("name"));
get("/App/pi",    (req, resp) -> String.valueOf(Math.PI));
start();
```

### 2. Annotation-based IoC (`MicroSpringBoot2.java`)

Load any POJO annotated with `@RestController` via command line using Java Reflection:

```java
@RestController
public class GreetingController {
    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return "Hola " + name;
    }
}
```

### Class Diagram

```
HttpServer          ← Core HTTP server (ServerSocket on port 8080)
├── WebMethod       ← Functional interface: (HttpRequest, HttpResponse) → String
├── HttpRequest     ← Encapsulates request path and query params
└── HttpResponse    ← Response object

MicroSpringBoot2    ← Reflection-based IoC loader
├── @RestController ← Marks a class as a REST controller
├── @GetMapping     ← Maps a method to a URL path
└── @RequestParam   ← Binds a query parameter to a method argument

Controllers
├── HelloController     ← Static methods, multiple @GetMapping routes
└── GreetingController  ← Instance method with @RequestParam
```

### Key Components

| Class              | Description                                                                            |
| ------------------ | -------------------------------------------------------------------------------------- |
| `HttpServer`       | Accepts TCP connections, parses HTTP requests, dispatches to endpoints or static files |
| `MicroSpringBoot2` | Uses `Class.forName()` + reflection to load controllers at runtime                     |
| `WebMethod`        | Functional interface enabling lambda endpoints                                         |
| `HttpRequest`      | Holds path and parsed query parameters                                                 |
| `@RestController`  | Annotation to mark a class as a web component                                          |
| `@GetMapping`      | Annotation to map a URL path to a method                                               |
| `@RequestParam`    | Annotation to extract and default query parameters                                     |

## Prerequisites

- Java JDK 17+
- Maven 3.x
- `JAVA_HOME` must point to a JDK (not a JRE)

To set `JAVA_HOME` temporarily in PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
```

## Build and Run

> Note: in this VS Code environment Maven may not be available in PATH. On your machine/CI, ensure `mvn` works.

### Build

```powershell
cd SpringBoot
mvn clean test package
```

### Run the lambda-style server

```powershell
java -cp target/classes edu.escuelaing.arep.App
```

Then open: [http://localhost:8080/index.html](http://localhost:8080/index.html)

### Run the reflection-based IoC server

```bash
java -cp target/classes edu.escuelaing.arep.MicroSpringBoot2 \
  edu.escuelaing.arep.GreetingController \
  /greeting?name=Juan
```

## Available Endpoints (App.java)

| Method | URL                 | Response               |
| ------ | ------------------- | ---------------------- |
| GET    | `/App/hello?name=X` | `Hello X`              |
| GET    | `/App/pi`           | `3.141592653589793`    |
| GET    | `/App/euler`        | `e= 2.718281828459045` |
| GET    | `/index.html`       | Static HTML page       |

## Available Endpoints (MicroSpringBoot2 + HelloController)

```bash
java -cp target/classes edu.escuelaing.arep.MicroSpringBoot2 \
  edu.escuelaing.arep.HelloController /
# → Greetings from Spring Boot!

java -cp target/classes edu.escuelaing.arep.MicroSpringBoot2 \
  edu.escuelaing.arep.HelloController /hello
# → Hello World

java -cp target/classes edu.escuelaing.arep.MicroSpringBoot2 \
  edu.escuelaing.arep.GreetingController /greeting?name=Juan
# → Hola Juan

java -cp target/classes edu.escuelaing.arep.MicroSpringBoot2 \
  edu.escuelaing.arep.GreetingController /greeting
# → Hola World  (uses defaultValue)
```

## Tests

```powershell
cd SpringBoot
mvn test
```

Tests cover:

- `HelloController` return values for all routes
- `GreetingController.greeting()` with and without a name
- `@RestController` annotation presence on both controllers
- `@GetMapping` annotation value on `HelloController.index()` and `GreetingController.greeting()`
- `@RequestParam` annotation value and `defaultValue` on `GreetingController.greeting()`
- `HttpRequest.getValues()` returns correct value and empty string for missing keys

### Test evidence

```
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

This project also includes integration-style tests in `AppTest` that:

- Send multiple HTTP requests concurrently to the server (`/slow`) and assert the total wall time is less than sequential time.
- Call `HttpServer.stop()` and assert the port is released.

> Update the block above with a screenshot or console output from your `mvn test` run.

## Concurrency design

`HttpServer` now:

- Accepts connections in the main accept loop.
- Dispatches each accepted socket to a **fixed** `ExecutorService`.
- Keeps server state with an `AtomicBoolean running`.

This allows multiple requests to be processed in parallel, bounded by the size of the worker pool.

## Graceful shutdown design

`HttpServer` registers a shutdown hook:

- When the JVM receives SIGTERM (Docker stop / EC2 service stop), the hook triggers `HttpServer.stop()`.
- `stop()` flips the `running` flag, closes the `ServerSocket`, and calls `shutdown()` + `awaitTermination()` on the worker pool.

This matches the rubric requirement: terminate the loop condition and shutdown using a Runtime Hook running in its own thread.

Reference: https://www.baeldung.com/jvm-shutdown-hooks

## Docker 🐳

There’s a `Dockerfile` in `SpringBoot/`.

### Build image

```powershell
cd SpringBoot
mvn clean test package
docker build -t microspringboot:latest .
```

### Run container

```powershell
docker run --rm -p 8080:8080 microspringboot:latest
```

Open:

- http://localhost:8080/index.html
- http://localhost:8080/App/hello?name=AREP

### Evidence (add screenshots)

Add screenshots here:

- Docker image built successfully
- Container running (`docker ps`)
- Browser hitting the endpoints

## AWS Deployment

### Summary

Deployed as a Docker container on an EC2 instance. The server shuts down gracefully when the container is stopped.

### Steps (example)

1. Create an EC2 instance (Ubuntu) and open inbound port **8080** in the Security Group.
2. Install Docker in EC2.
3. Copy the project or just the built image instructions.
4. Build and run:

```bash
docker build -t microspringboot:latest .
docker run -d --name microspringboot -p 8080:8080 microspringboot:latest
```

5. Test from your local machine:

- `http://<EC2_PUBLIC_IP>:8080/index.html`
- `http://<EC2_PUBLIC_IP>:8080/App/pi`

### Evidence (required by rubric)

Add images to the repo (for example in `docs/img/`):

- EC2 instance details (public IP)
- Security group showing port 8080
- `docker ps` showing the container
- Browser proofs calling endpoints

## Project Structure

```
src/
├── main/java/edu/escuelaing/arep/
│   ├── App.java                  ← Entry point (lambda API)
│   ├── HttpServer.java           ← Core HTTP server
│   ├── HttpRequest.java          ← Request model
│   ├── HttpResponse.java         ← Response model
│   ├── WebMethod.java            ← Functional interface
│   ├── MicroSpringBoot2.java     ← IoC loader via reflection
│   ├── HelloController.java      ← Example controller
│   ├── GreetingController.java   ← Example controller with @RequestParam
│   ├── GetMapping.java           ← @GetMapping annotation
│   ├── RestController.java       ← @RestController annotation
│   └── RequestParam.java         ← @RequestParam annotation
├── main/resources/webroot/
│   └── index.html                ← Static HTML page
└── test/java/edu/escuelaing/arep/
    └── AppTest.java              ← Unit tests
```

## Deliverables checklist (rubric)

- [x] GitHub-ready Maven project structure
- [x] `pom.xml`
- [x] `.gitignore` (excludes `target/`)
- [x] Concurrent request handling (thread pool)
- [x] Graceful shutdown (JVM shutdown hook)
- [x] Automated tests (`mvn test`)
- [x] Dockerfile + instructions
- [ ] Screenshots of Docker + AWS deployment (add in `docs/img/`)
- [ ] Video showing deployments working (record and add link here)
