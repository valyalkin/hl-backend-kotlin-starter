# hl-backend-kotlin-starter

A Spring Boot (Kotlin) starter service. This repository is cloned to create a new
Consumer Service.

## Package layout

The root package is `com.hl.service` and is invariant — it is never renamed when
the starter is cloned into a service.

Under the root package there are exactly seven concern packages. Each one holds
every resource's classes for that concern; there is no per-resource subpackage
requirement, though a resource may group its own files with a shared filename
prefix (`Widget*`) if a package grows large.

| Package | Holds |
| --- | --- |
| `com.hl.service.controller` | `@RestController` classes and the single `@RestControllerAdvice` `GlobalExceptionHandler` |
| `com.hl.service.service` | `@Service` classes; business logic; `@Transactional`, `@Cacheable` / `@CacheEvict` |
| `com.hl.service.repository` | Spring Data `JpaRepository` interfaces and their `@Entity` types |
| `com.hl.service.model` | Plain immutable domain classes |
| `com.hl.service.dto` | Request / response DTOs (`<Resource>Request`, `<Resource>Response`) and the shared page envelope |
| `com.hl.service.error` | Exception types (`BusinessException`, `NotFoundException`, `SystemException`) |
| `com.hl.service.config` | `@Configuration` classes and framework wiring |

The starter ships with none of these classes yet — the table describes where
each kind of code belongs as a service is built out (Epic 2 onward). Every
package directory currently holds only an empty `.gitkeep` so it survives a
fresh clone; delete that file once the package contains a real class.

Dependencies point the conventional Spring direction — controller → service →
repository — but this layout is a **naming convention only**. There is no
ArchUnit test, dependency-direction check, or any other build-time boundary
enforcement: nothing fails the build if a resource places a class in a different
package.

`StarterApplication.kt` sits directly under the root package and is the single
`@SpringBootApplication` entry point.
