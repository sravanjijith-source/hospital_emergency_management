# Hospital Emergency Management System (HEMS)

A production-grade, native desktop JavaFX application for hospital emergency triage, physician roster tracking, and patient lifecycle management. Powered by genuine Object-Oriented Programming (OOP) in Java 17, an in-memory `PriorityQueue`, and persistent relational storage in MySQL 8.0 via JDBC.

---

## 1. Prerequisites & Technology Stack

| Component | Version Used | Role & Specification |
| :--- | :--- | :--- |
| **Java JDK** | Eclipse Temurin 17.0.19+10 (LTS) | Core language runtime (Java 17) |
| **Maven** | Apache Maven 3.10.0+ | Dependency management and build lifecycle |
| **JavaFX** | OpenJFX 17.0.10 | Native desktop GUI (Controls, Graphics, FXML) |
| **Database** | MySQL 8.0.x (Local Windows Service) | Relational storage engine (InnoDB, utf8mb4) |
| **JDBC Driver** | MySQL Connector/J 8.3.0 | Type-4 JDBC connectivity with PreparedStatements |
| **Testing** | JUnit Jupiter 5.10.2 | Automated unit and integration testing |

---

## 2. Installing and Starting MySQL on Windows

1. Download and run the **MySQL Installer for Windows** (8.0.x) from the official MySQL website.
2. Choose **Server only** or **Developer Default**.
3. During setup, configure MySQL as a Windows Service named `MySQL80` running on port `3306`.
4. To check or start the MySQL service from Windows PowerShell (Run as Administrator if needed):
   ```powershell
   # Check service status
   Get-Service MySQL80

   # Start service if stopped
   Start-Service MySQL80
   ```

---

## 3. Creating the Database and Dedicated User

Open PowerShell or Command Prompt and connect to MySQL as `root`:
```powershell
mysql -u root -p
```
Execute the following SQL script to create the production database (`hems`), the test database (`hems_test`), and a dedicated least-privilege user (`hems_user`):

```sql
-- Create production and integration test databases
CREATE DATABASE IF NOT EXISTS hems CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS hems_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create least-privilege local user
CREATE USER IF NOT EXISTS 'hems_user'@'localhost' IDENTIFIED BY 'Hospital@2026';

-- Grant DDL and DML permissions on both databases
GRANT ALL PRIVILEGES ON hems.* TO 'hems_user'@'localhost';
GRANT ALL PRIVILEGES ON hems_test.* TO 'hems_user'@'localhost';
FLUSH PRIVILEGES;
```

---

## 4. Configuring Database Credentials

HEMS supports two credential loading methods. Environment variables take priority, falling back to a local configuration file.

### Option A: Environment Variables (Recommended)
Set the credentials in Windows PowerShell:
```powershell
# Current PowerShell session
$env:HEMS_DB_URL="jdbc:mysql://localhost:3306/hems"
$env:HEMS_DB_USER="hems_user"
$env:HEMS_DB_PASSWORD="Hospital@2026"

# Or persist permanently in Windows user profile
[Environment]::SetEnvironmentVariable("HEMS_DB_URL", "jdbc:mysql://localhost:3306/hems", "User")
[Environment]::SetEnvironmentVariable("HEMS_DB_USER", "hems_user", "User")
[Environment]::SetEnvironmentVariable("HEMS_DB_PASSWORD", "Hospital@2026", "User")
```

### Option B: Local Properties File (`config/db.properties`)
1. Copy `config/db.properties.example` to `config/db.properties`:
   ```powershell
   Copy-Item config/db.properties.example config/db.properties
   ```
2. Update `config/db.properties` with your credentials:
   ```properties
   db.url=jdbc:mysql://localhost:3306/hems?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   db.user=hems_user
   db.password=Hospital@2026
   ```
> **Security Note:** `config/db.properties` is explicitly ignored by `.gitignore` and must never be committed to source control. Passwords are never logged or displayed on UI error screens.

---

## 5. Schema Initialization

HEMS automatically verifies and initializes all required tables idempotently on application startup using `SchemaInitializer`. No manual DDL execution is necessary.

If you wish to inspect or run the schema manually, the complete script is provided in `schema.sql`:
```powershell
mysql -u hems_user -p hems < schema.sql
```

### Relational Schema Summary:
- **`patients`**: Stores permanent patient identities (`patient_id VARCHAR(20) PRIMARY KEY`, `full_name VARCHAR(100)`, `age INT` [0-120], `created_at DATETIME`).
- **`doctors`**: Attending physicians (`doctor_id VARCHAR(20) PRIMARY KEY`, `full_name VARCHAR(100)`, `age INT` [18-90], `specialization VARCHAR(80)`, `availability ENUM('AVAILABLE','BUSY','OFF_DUTY')`).
- **`id_sequences`**: Sequence counter table (`sequence_name VARCHAR(20) PRIMARY KEY`, `` `last_value` BIGINT NOT NULL ``). Stores rows `'PATIENT'` and `'DOCTOR'` for atomic ID generation.
- **`emergency_visits`**: Individual emergency encounters (`visit_id BIGINT AUTO_INCREMENT PRIMARY KEY`, `patient_id`, `doctor_id`, `severity`, `status`, `arrival_time`, `start_time`, `end_time`).
  - Foreign keys to `patients` and `doctors`.
  - Stored generated columns `active_doctor_id` and `open_patient_id` backed by UNIQUE constraints ensure at the database engine level that a physician can treat at most one patient at a time, and a patient can have at most one open visit at a time.
  - CHECK constraints validate that status transitions preserve required timestamps (`start_time`, `end_time`).

---

## 6. How to Build and Run the Application

### Launching the Native JavaFX Desktop UI
```powershell
mvn javafx:run
```
The application opens as a native, resizable desktop window (minimum size: 1050x680, default: 1180x740) styled in the Clinical Editorial palette (Midnight Navy `#172B3A`, Muted Teal `#167D83`, Sage `#DCE8DF`, Coral `#D94F45`, Amber `#D89B32`).

---

## 7. Automated Testing (JUnit 5)

HEMS includes **36 automated tests** across pure business logic and live database persistence:

```powershell
# Run ALL 36 automated tests (pure logic + MySQL hems_test integration tests)
mvn clean test

# Run ONLY pure-logic unit tests (runs in under 1 second without touching MySQL)
mvn test -Dtest=HospitalSystemPureLogicTest

# Run MySQL-dependent auto-generated ID tests
mvn test -Dtest=IdGenerationDatabaseTest
```

### Test Coverage Highlights:
1. **Auto-Generated ID Verification (`IdGenerationDatabaseTest`):**
   - **a)** First patient receives `P-0001` and first doctor receives `D-001` on a clean database.
   - **b)** IDs increment sequentially and maintain strict uniqueness across many registrations.
   - **c)** Multi-threaded concurrent registrations never generate duplicate IDs (race-free guarantee).
   - **d)** Registration failure and transaction rollback does not consume or waste sequence numbers.
   - **e)** System restart simulation continues sequentially from the last persisted sequence value.
   - **f)** Existing legacy IDs are preserved, sequence seeds from highest matching pattern suffix, and colliding numbers are safely skipped.
   - **g)** Case-insensitive exact ID search (e.g. `p-0001`, `d-001`) and partial name search operate cleanly.
2. **Queue Priority & Tie-Breaking:** Verifies 3-tier deterministic comparator: Severity (CRITICAL &rarr; HIGH &rarr; MEDIUM &rarr; LOW), then earlier arrival time, then lowest visit ID.
3. **Atomic Triage Assignment:** Verifies assigning the highest-priority patient locks visit and doctor, transitions visit to `IN_TREATMENT`, marks doctor `BUSY`, and leaves queue untouched if no doctor is free.
4. **Treatment Completion:** Verifies recording end timestamp, transitioning to `COMPLETED`, returning doctor to `AVAILABLE`, and rejecting repeated completion.
5. **Returning Patients:** Verifies patient identity persists across multiple visits; rejects new visits while one is currently open.
6. **Restart Recovery & Reconciliation:** Simulates stopping the application and restarting with a fresh service instance, verifying priority queue snapshot and doctor states are faithfully rebuilt from MySQL. Orphaned `BUSY` doctors without active visits are safely reconciled to `AVAILABLE`.
7. **Fail-Fast Unreachable MySQL:** Verifies clean exception handling and password concealment when MySQL is down.

---

## 8. Package Structure and Architecture

```
src/main/java/hems/
├── MainApp.java                       (JavaFX entry point & database pre-flight checks)
├── model/                             (Domain Entities - Encapsulated OOP)
│   ├── Person.java                    (Abstract parent: id, name, age, getRole())
│   ├── Patient.java                   (Extends Person: createdAt, historyCache)
│   ├── Doctor.java                    (Extends Person: specialization, availability)
│   ├── TreatmentRecord.java           (Emergency visit, deterministic comparator)
│   ├── Severity.java                  (CRITICAL=1, HIGH=2, MEDIUM=3, LOW=4)
│   ├── DoctorStatus.java              (AVAILABLE, BUSY, OFF_DUTY)
│   └── TreatmentStatus.java            (WAITING, IN_TREATMENT, COMPLETED)
├── database/                          (Connection & DDL Management)
│   ├── DatabaseConfig.java            (Environment variable & properties loader)
│   ├── ConnectionProvider.java        (Thread-safe DriverManager connection provider)
│   └── SchemaInitializer.java         (Idempotent non-destructive DDL executor)
├── dao/                               (Data Access Objects - Pure JDBC)
│   ├── PatientDao.java                (CRUD, case-insensitive ID & name search)
│   ├── DoctorDao.java                 (CRUD, case-insensitive ID search, status updates)
│   ├── TreatmentRecordDao.java        (SELECT ... FOR UPDATE locks, visit lifecycle)
│   └── SequenceDao.java               (Pessimistic row locking, ID generation & seeding)
├── service/                           (Business Logic & Synchronization)
│   ├── HospitalSystem.java            (Fair ReentrantLock, PriorityQueue, reconciliation)
│   └── DashboardStats.java            (Immutable statistics snapshot)
├── util/                              (Validation, Async, Formatting & Exceptions)
│   ├── Validator.java                 (Input boundaries & regex validation)
│   ├── AsyncExecutor.java             (Daemon thread pool offloading JDBC from UI thread)
│   ├── UiUtil.java                    (Badges, stat cards, confirmation dialogs)
│   ├── VectorIcons.java               (Lightweight high-DPI SVG icons)
│   ├── DateTimeUtil.java              (Human-readable dates, times, and duration timers)
│   ├── HospitalException.java         (Base unchecked application exception)
│   ├── ValidationException.java
│   ├── NotFoundException.java
│   ├── InvalidStateException.java
│   ├── NoAvailableDoctorException.java
│   └── DatabaseException.java
└── view/                              (Clinical Editorial UI Components)
    ├── MainView.java                  (Master layout: navy sidebar, header, alert banners)
    ├── DashboardView.java             (9 stat cards, queue preview, active treatments)
    ├── PatientsView.java              (Patient registration, returning visits, search)
    ├── DoctorsView.java               (Physician roster, search, availability toggle)
    ├── EmergencyQueueView.java        (Full priority table, elapsed timers, assign action)
    ├── ActiveTreatmentsView.java      (Ongoing procedures, complete treatment dialog)
    ├── PatientHistoryView.java        (MySQL retrospective visit history search)
    ├── SettingsView.java              (Connection diagnostics, reload/reconciliation)
    └── css/
        └── application.css            (Restrained clinical styling: navy, teal, sage)
```

---

## 9. Auto-Generated Patient & Doctor IDs Architecture

### Format & Numbering
- **Patients:** `P-0001`, `P-0002`, `P-0003`, ... formatted via `P-%04d`.
- **Doctors:** `D-001`, `D-002`, `D-003`, ... formatted via `D-%03d`.
- Numbers expand naturally beyond padding width (`P-10000`) without truncation.

### UI & Registration Behavior
- Manual ID text fields are removed from the registration forms in both `PatientsView` and `DoctorsView`.
- Forms present a read-only hint badge: `"ID is assigned automatically (e.g. P-0001)"` / `"ID is assigned automatically (e.g. D-001)"`.
- Upon successful registration, the notification explicitly reports the generated ID:
  - *Example:* `Patient Ravi Kumar registered with ID P-0007`
  - *Example:* `Doctor Dr. Ananya Sharma registered with ID D-004`
- Generated IDs automatically populate everywhere in the system: waiting priority table, ongoing treatments list, patient history view, doctor roster, and detail dialogs.
- Search supports exact ID matching case-insensitively (`P-0001` or `p-0001`) and partial name matching (`Ravi` or `ravi`).

### Sequence Storage & Seeding
- Backed by the `id_sequences` table in MySQL:
  ```sql
  CREATE TABLE IF NOT EXISTS id_sequences (
    `sequence_name` VARCHAR(20) PRIMARY KEY,
    `last_value`    BIGINT NOT NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ```
- **Legacy Record Preservation:** Existing records with older or manual IDs (`1000`, `1002`, `DOC-1`) remain completely intact.
- **Seeding from Existing Data:** On first initialization, `SequenceDao.ensureSeeded()` scans existing records. If IDs matching the prefix format already exist (e.g. `P-0007`), the counter seeds at that highest numeric value (`7`), so the next registration yields `P-0008`. If no pattern records exist, the counter seeds at `0` so the first generated ID is `1`.
- **Collision Protection:** If a generated candidate ID already exists in the database (such as a legacy manual entry), `SequenceDao` detects it via `existsById()` and skips to the next number before inserting.

---

## 10. Viva Defense & Conceptual Explanations

### Object-Oriented Programming (OOP)
- **Inheritance:** An abstract class `Person` encapsulates common attributes (`id`, `name`, `age`). `Patient` and `Doctor` inherit from `Person`, avoiding code duplication.
- **Polymorphism:** `Person.getRole()` is an abstract method. In runtime collections, `Patient.getRole()` returns `"Patient"` and `Doctor.getRole()` returns `"Doctor"`.
- **Encapsulation:** All model fields are `private` and `final` where applicable. Mutation occurs exclusively through validated domain methods (`startTreatment()`, `completeTreatment()`).
- **Enums with Fields:** `Severity` encapsulates integer priority levels (`CRITICAL = 1`, `HIGH = 2`, `MEDIUM = 3`, `LOW = 4`). `DoctorStatus` and `TreatmentStatus` embed state-machine transition validation (`canMoveTo()`, `canChangeManuallyTo()`).

### PriorityQueue vs Linear Lists
- A standard FIFO queue (`LinkedList`) treats patients on a first-come, first-served basis, which is dangerous in an emergency department.
- A `PriorityQueue` uses a binary min-heap where insertion and extraction are $O(\log n)$. It guarantees that regardless of arrival order, the highest clinical acuity (`CRITICAL`) is always prioritized first.
- **Ordered Snapshot:** Iterating a `PriorityQueue` directly does not yield elements in priority order (it only guarantees the head is minimal). To display the queue in the UI, `HospitalSystem.getWaitingQueueSnapshot()` copies the queue into a `List` and sorts it with `TreatmentRecord.PRIORITY_ORDER`.

### Concurrency & Thread Separation
- Running JDBC operations on the JavaFX Application Thread causes the interface to freeze and drop frames. All database calls are offloaded to `AsyncExecutor` worker threads.
- All state-altering service methods in `HospitalSystem` are serialized using a fair `java.util.concurrent.locks.ReentrantLock`, preventing assignment and completion race conditions.

### Viva Note: Why Lock the Counter Row with `SELECT ... FOR UPDATE` Inside the Transaction?
In a multi-user clinical environment, multiple receptionists may register incoming emergency patients simultaneously.
1. **The Race Condition of `SELECT MAX()`:** If two threads read the maximum ID at the same instant using standard `SELECT`, both see `P-0005`. Both would attempt to insert `P-0006`, causing one transaction to crash with a primary key constraint violation.
2. **Pessimistic Row Locking (`SELECT ... FOR UPDATE`):** When Thread A executes `SELECT last_value FROM id_sequences WHERE sequence_name = 'PATIENT' FOR UPDATE`, MySQL's InnoDB storage engine places an exclusive row-level lock on the `'PATIENT'` sequence record. Thread B attempting the same query is forced to wait until Thread A completes.
3. **Transactional Integrity & Rollback Safety:** By executing the sequence fetch, increment, and record insertion within the **same database transaction** (`conn.setAutoCommit(false)`):
   - If the patient insertion succeeds, both the new record and the updated sequence value commit together.
   - If the patient registration fails (e.g. database constraint or connection drop), `conn.rollback()` rolls back the sequence counter row as well. This prevents sequence "holes" or numbers being consumed erroneously.
4. **Why not MySQL `AUTO_INCREMENT`?** Standard MySQL `AUTO_INCREMENT` operates outside transaction rollback boundaries; if an insert fails, an `AUTO_INCREMENT` value is permanently lost. Furthermore, `AUTO_INCREMENT` only produces raw integers, whereas hospital governance requires prefixed alphanumeric identifiers (`P-0001`, `D-001`). The transactional counter table provides strict sequence continuity and format compliance.

---

## 11. Troubleshooting Guide

| Problem | Cause | Resolution |
| :--- | :--- | :--- |
| **Connection Refused (Port 3306)** | MySQL service is not running | Run `Start-Service MySQL80` in PowerShell. Verify port 3306 is listening using `Test-NetConnection -Port 3306 localhost`. |
| **Access Denied for 'hems_user'** | Incorrect password or missing database grants | Verify password in `$env:HEMS_DB_PASSWORD` or `config/db.properties`. Re-run the GRANT statements from Section 3. |
| **Public Key Retrieval Not Allowed** | MySQL 8.0 `caching_sha2_password` authentication requirement | Ensure JDBC URL includes `allowPublicKeyRetrieval=true&useSSL=false`. This is already configured in `DatabaseConfig`. |
| **Server Timezone Error** | MySQL server timezone not recognized by Connector/J | Add `serverTimezone=UTC` or `serverTimezone=SYSTEM` to the JDBC URL. |
| **JavaFX Graphics Modules Missing** | Running `java` without JavaFX module path | Always launch via `mvn javafx:run`, which configures modular classpath arguments automatically. |
| **Java Version Mismatch** | System default `java` is not Java 17 | Verify `java -version` reports `17.0.x`. Point `JAVA_HOME` to your JDK 17 installation. |

---

## 12. Known Limitations

- **Single Clinic Scope:** Designed for a single emergency department; does not support multi-facility network federation or ambulance telematics.
- **In-Memory PriorityQueue Mirroring:** The priority queue is cached in JVM memory for sub-millisecond sorting and snapshotting. High-availability multi-instance clustering would require distributed queue coordination (e.g. Redis or database polling).
- **No Role-Based Access Control (RBAC):** Intentionally designed without user login/roles per project specifications to maintain focus on clinical emergency workflow simulation.
