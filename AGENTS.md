# Repository Guidelines

## Project Structure & Module Organization
- Spring Boot Gradle project using Java 25; root build files: `build.gradle`, `settings.gradle`, `gradlew`.
- Application configuration lives in `src/main/resources/application.properties`; add profiles via `application-<profile>.properties`.
- Place application code under `src/main/java/...`; keep domain, messaging, web, and config packages separate. Static assets/templates reside in `src/main/resources/static` and `templates`.
- Tests belong in `src/test/java/...`, mirroring main package names one-to-one.

## Build, Test, and Development Commands
- `./gradlew clean build` — full compile, run tests, and package the app.
- `./gradlew test` — run JUnit-based unit/integration tests only.
- `./gradlew bootRun` — start the app locally using the active profile; prefer `.env` or environment variables for secrets.
- `./gradlew dependencies` — inspect resolved dependency graph if version issues appear.

## Coding Style & Naming Conventions
- Use 4-space indentation, UTF-8 source, and trailing newline; avoid tab characters.
- Package names should follow `com.example.<context>`; classes use PascalCase; methods/fields use lowerCamelCase; constants UPPER_SNAKE_CASE.
- Prefer constructor injection for components; use SLF4J logging (`log.info/debug`) instead of `System.out`.
- Keep controllers thin; encapsulate RabbitMQ interactions behind dedicated services/ports.

## Testing Guidelines
- JUnit 5 is available via Spring Boot starters; name test classes `*Test` and align package to the class under test.
- Default to fast unit tests; use `@SpringBootTest` or test slices only when needed for wiring/messaging.
- Ensure new behavior ships with tests; aim to cover message routing edge cases and validation paths.

## Commit & Pull Request Guidelines
- Write commits in present tense and focused on one change (e.g., `Add message publisher for order events`).
- Include brief context in the body when touching messaging contracts or configuration.
- PRs should describe scope, testing performed, and any RabbitMQ/broker assumptions; attach screenshots for API/HTTP changes when relevant.
- Reference related tickets/issues in PR descriptions; prefer small, reviewable changes over large drops.

## Security & Configuration Tips
- Do not commit secrets; rely on environment variables or profile-specific property files excluded from VCS.
- When adding RabbitMQ creds/URLs, prefer properties `spring.rabbitmq.*` and document defaults; validate host/port via health checks.
