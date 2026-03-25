# Bayu

Lightweight Spring-like Java framework optimized for native compilation. Built with compile-time annotation processing — zero reflection at runtime.

**Bayu** means "big wind" in Indonesian.

## Why

Spring Boot native-image compilation is slow (3-5 min), uses too much RAM (6-8 GB), and produces large binaries (150+ MB). Bayu generates plain Java code at compile time via annotation processing, resulting in:

| | Bayu Native | Spring Boot Native | Spring Boot JVM |
|---|---|---|---|
| Build time | **~60s** | 3-5 min | 10-15s |
| Build RAM | **2.5 GB** | 6-8 GB | ~1 GB |
| Binary size | **~80 MB** | 150-200 MB | ~30 MB jar |
| RSS (idle) | **~60 MB** | 80-120 MB | 300-500 MB |
| Startup | **instant** | 50-200ms | 2-5s |

*Benchmarked with a real POS system: 16 entities, 72 REST endpoints, JWT auth, AI integration, H2 database.*

## Features

- **DI Container** — `@Component`, `@Service`, `@Autowired`, `@Bean`, `@Configuration`, `@Value`
- **REST API** — `@RestController`, `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`
- **Data Layer** — `@Entity`, `@Repository`, `BayuRepository<T, ID>` with generated JDBC implementations
- **Query Derivation** — `findByName`, `findByAgeGreaterThan`, `countByStatus` (parsed at compile time into SQL)
- **@OneToMany** — Automatic cascade load/save/delete for nested entities (up to 3 levels deep)
- **@ElementCollection** — Lists stored as JSON columns
- **UUID Primary Keys** — Auto-generated UUIDs with `@Id @GeneratedValue`
- **Audit Fields** — `@CreatedAt`, `@UpdatedAt` auto-set on save
- **DDL Auto-Create** — Tables created from entity definitions at startup
- **Security** — `@Interceptor`, `@Secured("ROLE_ADMIN")` for RBAC
- **JWT** — Built-in `JwtUtil` for token generation/validation (HMAC-SHA256, zero dependencies)
- **Password Hashing** — `PasswordEncoder` with SHA-256 + salt
- **CORS** — Configurable via `application.yml`
- **Pagination** — `Page<T>`, `PageRequest` with LIMIT/OFFSET
- **Transaction** — `TransactionManager` with ThreadLocal connection sharing
- **HTTP Client** — `BayuHttpClient` wrapping `java.net.http.HttpClient`
- **WebSocket** — `@WebSocket("/path")` with Undertow native support
- **Config** — `application.yml` with profiles, env var override, `${KEY:default}` expressions
- **Base CRUD** — `BaseCrudController<T>` + `ApiResponse` wrapper for instant REST APIs
- **Lifecycle** — `BayuStartup` interface for post-init hooks (data seeding, etc.)
- **Undertow** — Production-grade HTTP server with virtual thread-like performance

## Quick Start

```java
@BayuApplication
public class App {
    public static void main(String[] args) {
        Bayu.run(App.class, args);
    }
}

@RestController
@RequestMapping("/api/users")
public class UserController extends BaseCrudController<User> {
    @Autowired
    public void setService(UserService service) { this.service = service; }
    protected BaseCrudService<User, UUID> getService() { return service; }
    private UserService service;
}

@Service
public class UserService implements BaseCrudService<User, UUID> {
    @Autowired
    public void setRepository(UserRepository repo) { this.repo = repo; }
    private UserRepository repo;

    public List<User> findAll() { return repo.findAll(); }
    public Optional<User> findById(UUID id) { return repo.findById(id); }
    public User save(User entity) { return repo.save(entity); }
    public void deleteById(UUID id) { repo.deleteById(id); }
    // ...
}

@Entity
@Table("users")
public class User {
    @Id @GeneratedValue
    private UUID id;
    private String name;
    private String email;
    @CreatedAt private LocalDateTime createdAt;
    @UpdatedAt private LocalDateTime updatedAt;
    // getters + setters
}

@Repository
public interface UserRepository extends BayuRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByNameContaining(String name);
}
```

```yaml
# application.yml
server:
  port: 8080
  cors:
    origins: "http://localhost:5173"

datasource:
  url: jdbc:h2:mem:mydb;DB_CLOSE_DELAY=-1
  username: sa
  password:
```

## Project Structure

```
bayu/
├── bayu-core/        # DI, config, lifecycle
├── bayu-web/         # REST, Undertow, WebSocket, CORS, HTTP client
├── bayu-data/        # Repository, entities, transactions, pagination
├── bayu-security/    # Interceptors, JWT, password hashing, RBAC
├── bayu-processor/   # Compile-time annotation processor (the brain)
├── bayu-starter/     # Bundles everything
└── bayu-example/     # Demo app
```

## How It Works

Bayu uses Java Annotation Processing (APT) to generate a `BayuGeneratedContext` class at compile time. This class contains:

- All bean instantiation and dependency wiring (no reflection)
- HTTP route registration from `@RestController` methods
- JDBC repository implementations from `@Repository` interfaces
- Cascade load/save/delete for `@OneToMany` relationships
- DDL statements for auto-creating tables

The generated code is plain readable Java — you can open it and debug it.

## Build

```bash
mvn clean install
```

## Native Image

```bash
mvn package -Pnative -DskipTests
```

Requires GraalVM with `native-image`.

## Modules

### bayu-core
DI annotations, `Bayu.run()` bootstrap, `ConfigLoader` for YAML config.

### bayu-web
Undertow HTTP server, REST annotations, trie-based router, `RequestContext`/`ResponseWriter`, WebSocket support, `BayuHttpClient`, `BaseCrudController`, `ApiResponse`.

### bayu-data
Entity annotations (`@Entity`, `@Table`, `@Id`, `@Column`, `@OneToMany`, `@ElementCollection`, `@CreatedAt`, `@UpdatedAt`), `BayuRepository<T, ID>`, `TransactionManager`, `Page<T>`, `SchemaGenerator`.

### bayu-security
`@Interceptor`, `@Secured`, `BayuInterceptor`, `SecurityContext`, `JwtUtil`, `PasswordEncoder`.

### bayu-processor
The annotation processor that generates all wiring code. Handles: bean discovery, dependency resolution (topological sort with cycle detection), route extraction (with generic type resolution for inherited methods), repository JDBC code generation (CRUD + query derivation + @OneToMany cascade), DDL generation.

## License

MIT
