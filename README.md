# Auth Server GraalVM

This is an authentication server built with Spring Boot and optimized for GraalVM, offering high performance and fast startup time.

## Requirements

- Java 24 or higher
- GraalVM
- Gradle 8.x

## Technologies

- Spring Boot 3.5.3
- GraalVM Native Image
- Spring Security
- Gradle

## How to Run

### Development Mode

```bash
./gradlew bootRun
```

### Native Compilation

To generate a native image of the application:

```bash
./gradlew nativeCompile
```

The native executable will be generated at `build/native/nativeCompile/auth-server-graalvm`

### Running Native Version

```bash
./build/native/nativeCompile/auth-server-graalvm
```

## Build and Tests

To run the tests:

```bash
./gradlew test
```

To build the project:

```bash
./gradlew build
```
