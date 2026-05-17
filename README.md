# MS-USER — Microservicio de Gestión de Usuarios

Microservicio desarrollado con **Spring Boot 3.4.5** encargado de gestionar los perfiles de usuarios del sistema hospitalario. Se conecta a **NeonDB (PostgreSQL)** para persistir los datos y delega el registro de credenciales al microservicio **MS-AUTH** mediante OpenFeign.

---

## Tecnologías y Versiones

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 21 (LTS) | Lenguaje principal |
| Spring Boot | 3.4.5 | Framework principal |
| Spring Cloud | 2024.0.1 | Gestión de dependencias cloud |
| Gradle (Kotlin DSL) | 8.7 | Herramienta de build |
| Spring Data JPA | gestionado por Spring Boot 3.4.5 | Acceso a datos |
| Hibernate | 6.6.x (incluido en Spring Boot 3.4.5) | ORM |
| PostgreSQL Driver | 42.7.x (gestionado por Spring Boot 3.4.5) | Conector JDBC |
| PostgreSQL | 17 | Motor de base de datos (Docker / NeonDB) |
| Flyway | 10.20.x (gestionado por Spring Boot 3.4.5) | Migraciones de base de datos |
| Spring Cloud OpenFeign | gestionado por Spring Cloud 2024.0.1 | Comunicación con MS-AUTH |
| Spring Validation | gestionado por Spring Boot 3.4.5 | Validación de DTOs |
| Lombok | gestionado por Spring Boot 3.4.5 | Reducción de boilerplate |
| spring-dotenv | 4.0.0 | Carga automática del `.env` en local |
| Docker | 27+ | Contenedores y orquestación |
| eclipse-temurin | 21-jre | Imagen base JRE para Docker |

---

## Por qué Java 21 y no Java 25

Durante el desarrollo se intentó usar **Java 25**, pero generó incompatibilidades concretas con el stack elegido:

1. **Spring Cloud OpenFeign 4.2.1 — URL validation**: Java 25 cambió el comportamiento de `java.net.URL` al validar hosts. OpenFeign intenta resolver y validar la URL del cliente (`@FeignClient`) durante el registro de beans, antes de que el `Environment` de Spring haya cargado las propiedades del YAML. Con Java 25, esta validación falla con `MalformedURLException: Illegal character found in host: '{'` al encontrar el placeholder sin resolver `${ms-auth.base-url}`.

2. **Flyway 10.x — classloading conflict**: Flyway 10.x usa `ServiceLoader` para registrar sus plugins internos. Con Java 25 y el module system, aparece un `IncompatibleClassChangeError` al arrancar: `NullFlywayTelemetryManager` no puede implementar `FlywayTelemetryManager` porque la JVM carga dos versiones incompatibles de la misma clase desde diferentes módulos del classpath. Esto no ocurre con Java 21.

3. **Soporte oficial**: Spring Boot 3.4.x tiene soporte oficial para Java 21 (LTS) y Java 17. Java 25 no forma parte del conjunto de versiones validadas por Spring en esta rama.

---

## Gradle con Kotlin DSL (`build.gradle.kts`)

### Por qué Gradle y no Maven

Gradle y Maven son las dos herramientas de build más usadas en el ecosistema Java. Este proyecto usa **Gradle** por varias razones concretas:

- **Velocidad:** Gradle tiene un sistema de caché incremental. Si una tarea ya se ejecutó y nada cambió, no la vuelve a correr. Maven siempre ejecuta todo el ciclo de vida desde cero. En proyectos grandes esto puede ser la diferencia entre 10 segundos y 2 minutos de build.
- **Flexibilidad:** Gradle permite definir tareas personalizadas con lógica real. Maven está basado en XML y sus plugins son rígidos — extenderlo requiere escribir plugins Java separados.
- **Menos verbosidad:** el archivo de configuración de Gradle es significativamente más corto y legible que un `pom.xml` equivalente.

### Por qué Kotlin DSL (`.kts`) y no Groovy DSL

Gradle históricamente usaba Groovy como lenguaje de scripting (`build.gradle`). El archivo `.kts` usa **Kotlin** en su lugar, que es el estándar actual y el recomendado por Gradle desde la versión 5+.

| Característica | Groovy DSL (`build.gradle`) | Kotlin DSL (`build.gradle.kts`) |
|---|---|---|
| Autocompletado en IDE | Limitado | Completo |
| Detección de errores | En tiempo de ejecución | En tiempo de compilación |
| Tipado | Dinámico | Estático |
| Refactoring seguro | No | Sí |

El Kotlin DSL detecta errores de configuración antes de ejecutar el build, algo que con Groovy solo se descubre cuando ya falló.

### Partes importantes del `build.gradle.kts`

**1. Plugins**
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
}
```
- `org.springframework.boot` — habilita la tarea `bootJar` que empaqueta la aplicación como un JAR ejecutable (fat JAR con todas las dependencias incluidas). Sin este plugin, el JAR generado no incluiría Spring ni las librerías.
- `io.spring.dependency-management` — importa el BOM (Bill of Materials) de Spring Boot, que fija las versiones de todas las dependencias compatibles entre sí. Gracias a esto, no se especifica versión en la mayoría de las dependencias — Spring Boot garantiza que sean compatibles.

**2. Scopes de dependencias**
```kotlin
implementation(...)      // disponible en compilación y en runtime
runtimeOnly(...)         // solo en runtime, no en compilación
compileOnly(...)         // solo en compilación, no se incluye en el JAR
annotationProcessor(...) // procesador que corre durante la compilación (Lombok)
developmentOnly(...)     // solo en desarrollo local, excluido del JAR final
testImplementation(...)  // solo para tests
```
Esto es importante porque evita incluir código innecesario en el JAR de producción. Por ejemplo, `devtools` está marcado como `developmentOnly` — cuando Docker construye el JAR con `bootJar`, Spring Boot lo excluye automáticamente.

**3. Spring Cloud BOM**
```kotlin
extra["springCloudVersion"] = "2023.0.1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}
```
OpenFeign pertenece a Spring Cloud, no a Spring Boot. Este bloque importa el BOM de Spring Cloud 2023.0.1, que es la versión compatible con Spring Boot 3.2.x. Sin esto, la dependencia de OpenFeign no tendría versión fijada y podría generar conflictos.

**4. Lombok con annotation processor**
```kotlin
compileOnly("org.projectlombok:lombok")
annotationProcessor("org.projectlombok:lombok")
```
Lombok genera código en tiempo de compilación (getters, setters, builders, constructores). Necesita dos entradas: `compileOnly` para que esté disponible al compilar, y `annotationProcessor` para que el compilador lo invoque como procesador de anotaciones. Sin la segunda línea, las anotaciones como `@Data` o `@Builder` no generan nada.

**5. PostgreSQL como `runtimeOnly`**
```kotlin
runtimeOnly("org.postgresql:postgresql")
```
El driver JDBC de PostgreSQL solo se necesita cuando la aplicación está corriendo para abrir conexiones reales. En tiempo de compilación, el código solo interactúa con las interfaces de Spring Data JPA — no sabe qué base de datos hay detrás. Marcarlo como `runtimeOnly` reduce el tamaño del classpath en compilación y deja explícito que es una dependencia de infraestructura, no de lógica.

---

## Docker

### Por qué usamos Docker

En un sistema de microservicios, cada servicio tiene sus propias dependencias de infraestructura: bases de datos, puertos, variables de entorno. Sin Docker, cada desarrollador debe instalar y configurar PostgreSQL manualmente, asegurarse de que corre en el puerto correcto, crear las bases de datos a mano, y coordinar que MS-AUTH y MS-USER apunten al lugar correcto. Esto genera el clásico problema de "en mi máquina funciona".

Docker resuelve esto empaquetando tanto la aplicación como su entorno en contenedores reproducibles. Con un solo comando, cualquier persona puede levantar el stack completo idéntico al de cualquier otro desarrollador o al entorno de producción.

### Por qué lo aplicamos en este proyecto

Este sistema hospitalario está compuesto por al menos dos microservicios que se comunican entre sí (MS-USER y MS-AUTH), cada uno con su propia base de datos PostgreSQL. Coordinar esto manualmente es propenso a errores. Con Docker logramos:

- **Aislamiento:** cada microservicio corre en su propio contenedor con sus dependencias, sin interferir con otros procesos del sistema.
- **Base de datos reproducible:** el contenedor `postgres-hospital` levanta PostgreSQL 17 con las dos bases de datos (`msuser_db` y `msauth_db`) creadas automáticamente, sin pasos manuales.
- **Migraciones automáticas:** Flyway ejecuta los scripts SQL al iniciar cada servicio. La estructura de la base de datos siempre está sincronizada con el código, sin necesidad de correr scripts a mano.
- **Comunicación entre servicios:** Docker Compose define una red interna donde MS-USER puede alcanzar a MS-AUTH por nombre (`http://ms-auth:8080`) sin depender de IPs o configuraciones externas.
- **Arranque ordenado:** el servicio `postgres` tiene un healthcheck que garantiza que la base de datos esté lista antes de que MS-AUTH y MS-USER intenten conectarse.

### Arquitectura de contenedores

```
┌─────────────────────────────────────────────────────────┐
│                    Docker Network                        │
│                                                         │
│  ┌──────────────────┐        ┌──────────────────────┐  │
│  │   ms-user        │──────▶ │   ms-auth            │  │
│  │   puerto 8081    │        │   puerto 8080         │  │
│  └────────┬─────────┘        └──────────┬───────────┘  │
│           │                             │               │
│           ▼                             ▼               │
│  ┌──────────────────────────────────────────────────┐   │
│  │         postgres-hospital  (puerto 5432)          │   │
│  │   ├── msuser_db   (tabla: users)                 │   │
│  │   └── msauth_db  (tabla: user_credentials)       │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

## Patrones de Diseño

### 1. Repository Pattern

**Archivo:** `repository/UserRepository.java`

El patrón Repository actúa como una capa intermedia entre la lógica de negocio y la base de datos. En lugar de escribir queries SQL directamente en el código, toda la comunicación con NeonDB pasa por el repositorio.

```
Controller → UserClient → UserRepository → NeonDB
                               ↑
               nadie más sabe que existe PostgreSQL aquí
```

**Por qué lo usamos:**

- El resto del código no sabe ni le importa si la base de datos es PostgreSQL, MySQL u otra cosa. Si mañana migramos de NeonDB a otro proveedor, solo tocamos el repositorio.
- Permite declarar queries complejas como simples nombres de métodos (`findByEmail`, `existsByDocumentNumber`) sin escribir SQL.
- Facilita los tests: se puede reemplazar el repositorio por uno en memoria sin modificar la lógica.

```java
// En lugar de esto (acoplamiento directo a SQL):
String sql = "SELECT * FROM users WHERE email = ?";

// Usamos esto (Repository Pattern):
userRepository.findByEmail(email);
```

---

### 2. Facade Pattern

**Archivo:** `client/UserClient.java`

El patrón Facade proporciona una interfaz simplificada que oculta la complejidad de coordinar múltiples subsistemas. En este microservicio, registrar un usuario implica varios pasos internos que el Controller no necesita conocer.

```
Controller llama un solo método:
    userClient.registerUser(dto)

Por dentro el Facade coordina:
    1. Valida email y documento duplicado
    2. Mapea DTO → Entity
    3. Guarda en NeonDB via UserRepository
    4. Llama a MS-AUTH via AuthFeignClient
    5. Mapea Entity → ResponseDTO
    6. Retorna la respuesta
```

**Por qué lo usamos:**

- El Controller queda limpio: solo recibe la request HTTP y llama al Facade. No tiene lógica de negocio.
- Si el flujo de registro cambia (por ejemplo, agregar un paso de envío de email), solo se modifica `UserClient`, sin tocar el Controller.
- Separa claramente las responsabilidades: el Controller maneja HTTP, el Facade maneja la orquestación del negocio.

```java
// El Controller solo ve esto:
return ResponseEntity.status(201).body(userClient.registerUser(dto));

// La complejidad está encapsulada dentro del Facade
```

---

## Estructura del Proyecto

```
Ms-User/
├── Dockerfile
├── docker-compose.yml               ← orquesta MS-USER, MS-AUTH y PostgreSQL
├── docker/
│   └── init-dbs.sql                 ← crea msuser_db y msauth_db al iniciar postgres
└── src/main/
    ├── resources/
    │   ├── application.yml
    │   └── db/migration/
    │       └── V1__create_users.sql ← migración Flyway
    └── java/com/hospital/msuser/
        ├── MsUserApplication.java
        ├── config/
        │   └── FeignConfig.java
        ├── controller/
        │   └── UserController.java           ← recibe HTTP, delega al Facade
        ├── dto/
        │   ├── request/
        │   │   ├── CreateUserRequestDTO.java
        │   │   └── UpdateUserRequestDTO.java
        │   ├── response/
        │   │   └── UserResponseDTO.java
        │   └── auth/
        │       └── RegisterAuthRequestDTO.java  ← DTO que se envía a MS-AUTH
        ├── entity/
        │   ├── User.java
        │   └── enums/
        │       ├── UserRole.java             (PATIENT, DOCTOR, NURSE, ADMIN, RECEPTIONIST)
        │       └── DocumentType.java         (DNI, PASSPORT, FOREIGN_ID, RUC)
        ├── repository/
        │   └── UserRepository.java           ← Patron Repository
        ├── client/
        │   ├── UserClient.java               ← Patron Facade (capa de negocio)
        │   └── auth/
        │       └── AuthFeignClient.java       ← cliente HTTP hacia MS-AUTH
        └── exception/
            ├── GlobalExceptionHandler.java
            ├── UserNotFoundException.java
            └── UserAlreadyExistsException.java
```

---

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/users/register` | Registra un nuevo usuario |
| `GET` | `/api/users` | Lista todos los usuarios activos |
| `GET` | `/api/users/{id}` | Obtiene un usuario por ID |
| `PUT` | `/api/users/{id}` | Actualiza datos del usuario |
| `DELETE` | `/api/users/{id}` | Desactiva un usuario (soft delete) |

---

## Flujo de Registro

```
POST /api/users/register
        ↓
  UserController
        ↓
  UserClient (Facade)
     ├── Valida duplicados (email, documento)
     ├── Guarda User en NeonDB  →  tabla: users
     └── Llama a MS-AUTH via Feign  →  POST /api/auth/register
                                           ↓
                                     MS-AUTH guarda credenciales
                                     en su propia NeonDB
```

---

## Configuración

### Opción A — Docker (recomendado)

Levanta el stack completo (PostgreSQL + MS-AUTH + MS-USER) con un solo comando:

```bash
docker compose up --build
```

| Servicio | URL |
|---|---|
| MS-USER | http://localhost:8081 |
| MS-AUTH | http://localhost:8080 |
| PostgreSQL | localhost:5432 |

No se requiere ninguna configuración adicional. Flyway crea las tablas automáticamente al iniciar.

### Opción B — Local con NeonDB

Crear el archivo `.env` en la raíz del proyecto:

```env
DB_URL=jdbc:postgresql://HostNeonDB/neondb?sslmode=require&channel_binding=require
DB_USER=neondb_owner
DB_PASS=*********
MS_AUTH_URL=http://localhost:8080
```

```bash
./gradlew bootRun
```

El servicio inicia en el puerto **8081**.

### Opción C — Railway (producción)

El servicio está desplegado en Railway y es accesible públicamente en:

**`https://ms-user-production.up.railway.app`**

Railway corre el servicio usando el `Dockerfile` del repositorio. La base de datos PostgreSQL corre también en Railway en la misma red interna, por lo que MS-USER la alcanza sin exponerla a internet.

Variables de entorno configuradas en Railway:

| Variable | Descripción |
|----------|-------------|
| `DB_URL` | `jdbc:postgresql://postgres.railway.internal:5432/msuser_db` — apunta a PostgreSQL en la red interna de Railway |
| `DB_USER` | Usuario de la base de datos |
| `DB_PASS` | Contraseña de la base de datos |
| `MS_AUTH_URL` | `https://ms-auth-production-38c7.up.railway.app` — URL pública de MS-AUTH en Railway |
| `SERVER_PORT` | `8081` — puerto en el que escucha Spring Boot |

---

## Tests

Los tests cubren la capa HTTP y la lógica de negocio de forma aislada, sin levantar el contexto completo de Spring ni conectarse a ninguna base de datos.

| Clase | Enfoque | Tests |
|---|---|---|
| `UserControllerTest` | `@WebMvcTest` — prueba status codes, JSON y validaciones HTTP | 10 |
| `UserClientTest` | Mockito puro — prueba el Facade (`UserClient`) con repositorio y Feign mockeados | 11 |

```bash
./gradlew test
```

Los reportes HTML quedan en `build/reports/tests/test/index.html`.

---

## Base de Datos

- **Proveedor (producción):** NeonDB (PostgreSQL serverless) — `sa-east-1`
- **Proveedor (Docker/local):** PostgreSQL 17
- **Migraciones:** Flyway 10.20.x — scripts en `src/main/resources/db/migration/`
- **Tabla principal:** `users`
- **`ddl-auto`:** `none` — Hibernate no toca el esquema, Flyway es el único responsable de la estructura
