# LifeLine (RaktSetu)

LifeLine (RaktSetu) is an explainable, urgency-aware blood donor discovery and ranking platform designed to connect active blood requirements with compatible, eligible donors transparently and efficiently.

> [!CAUTION]
> **Clinical Disclaimer**: LifeLine performs algorithmic routing and ranking using standard ABO/Rh compatibility rules and configured donation intervals. It does not provide medical clearance. Final clinical eligibility, cross-matching, and component decisions remain the exclusive responsibility of qualified blood-bank personnel.

---

## Architecture & Modules

The platform is structured as a Maven multi-module system enforcing strict boundary separation:

| Module | Purpose | Allowed Dependencies |
|---|---|---|
| `lifeline-core` | Pure Java domain model, compatibility filtering (8x8 ABO/Rh), cooldown exclusion, proximity and rarity factors, greedy Top-N ranking, and explainability. No Spring dependencies. | None (pure Java) |
| `lifeline-sim` | Synthetic simulation world, latent donor traits, response models (M1, M2, M3), and ground truth. | `lifeline-core` (domain types only) |
| `lifeline-eval` | Baselines (S0–S8), experiment runners (E1–E9), metrics, and CSV result exports. | `lifeline-core`, `lifeline-sim` |
| `lifeline-api` | Spring Boot REST API, Spring Security (JWT + RBAC), PostgreSQL persistence, and NLP intake. | `lifeline-core` |
| `lifeline-arch-tests` | Dedicated ArchUnit test suite enforcing architectural layer boundaries. | All modules (test scope only) |
| `frontend/` | React user interface for staff, donors, and public intake. | Consumes REST API (OpenAPI) |

Architectural rules strictly forbid `lifeline-core` and `lifeline-api` from importing or depending on `lifeline-sim` or `lifeline-eval`. This rule is continuously verified by ArchUnit tests.

---

## Build & Test Instructions

### Prerequisites
- Java 21 (or newer JDK with release 21 support)
- Apache Maven 3.9+

### Commands

To build all modules and run all unit, property, and architectural boundary tests:

```bash
mvn -q verify
```

To run only the architectural boundary tests:

```bash
mvn test -pl lifeline-arch-tests -am
```
