# Notification Management Service — Backend

Spring Boot 3 / Java 17 service. See the repository root for full documentation:

- [`../EXECUTION_GUIDE.md`](../EXECUTION_GUIDE.md) — setup, running, testing, troubleshooting, tech stack
- [`../docs/architecture-overview.md`](../docs/architecture-overview.md) — diagrams and design rationale
- [`../docs/scenarios/`](../docs/scenarios/) — greenfield / brownfield / ambiguous-requirement write-ups
- [`../docs/testing-strategy.md`](../docs/testing-strategy.md) — approach, coverage, limitations

Quick start:

```bash
export JAVA_HOME=/path/to/your/jdk-17
mvn spring-boot:run    # http://localhost:8080, Swagger UI at /swagger-ui.html
mvn verify              # full test suite (unit + integration) + coverage gate
```
