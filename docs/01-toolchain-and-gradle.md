# Toolchain and Gradle

This file is the long form of **Step 0 and Step 1 (build tool only)**. There is still no Spring Boot code on this branch.

## What must be installed

| Tool | Role | This machine |
|------|------|----------------|
| Git | History, branches | 2.55.0 |
| JDK | Compile and run bytecode | Amazon Corretto **21** |
| Gradle | Download libraries, compile, run, test | **9.7.1** at `C:\gradle\gradle-9.7.1` |
| Docker Desktop | Later: Elasticsearch + Kibana processes | Installed; not used until the ES step |

Spring Boot 3.3+ needs **JDK 17 or newer**. We will use **21** because that is what is on PATH. We will not install a second JDK unless a library forces 17.

Maven is **not** required on this branch. If you see `pom.xml` or `mvnw`, you are on the wrong branch.

---

## Git: why a dedicated branch

`main` is an empty README. The finished Maven POC lives on another branch. This branch is the learning rebuild.

Commands you already used:

```powershell
git checkout main
git checkout -b cursor/learn-spring-es-gradle
```

If you mix the two branches, you will get Maven and Gradle in one head. Do not merge the old POC into this branch.

---

## JDK: what `java -version` actually told us

```text
openjdk version "21.0.12.1"
OpenJDK Runtime Environment Corretto-21.0.12.9.1
```

**Corretto** is Amazon’s OpenJDK build. Functionally it is Java 21. Spring Boot does not care that it is Corretto vs Temurin vs Oracle, as long as it is a full JDK (not a JRE-only install).

`JAVA_HOME` on this PC points at:

`C:\Program Files\Amazon Corretto\jdk21.0.12_9`

Gradle uses that for the **Daemon JVM** (the long-lived process that actually compiles). If `JAVA_HOME` is wrong but `java` on PATH is right, you get confusing “works in one terminal, fails in IDE” bugs. Keep them the same.

**Alternative:** install JDK 17 and set `JAVA_HOME` to 17 to match older company baselines. We skip that because 21 is the LTS you already have.

---

## Gradle PATH: the bug we already hit

You set:

```text
GRADLE_HOME = C:\gradle\gradle-9.7.1
```

Then `gradle -v` failed with “not recognized”.

Windows does not execute `GRADLE_HOME`. It searches **PATH** for `gradle.bat` / `gradle`. The file that must be on PATH is:

```text
C:\gradle\gradle-9.7.1\bin
```

That folder contains `gradle` (Unix script) and `gradle.bat` (Windows). After adding it to **User** PATH you must **open a new** PowerShell. Existing windows keep the old environment.

`GRADLE_HOME` is still useful: some docs and IDE fields expect it. It is not a substitute for PATH.

---

## What Gradle is (compared to “just javac”)

If you only had `javac`, you would:

1. Download Spring JARs by hand.
2. Put dozens of JARs on `--class-path`.
3. Compile in the right order.
4. Build a fat JAR that contains Tomcat.
5. Repeat this on every teammate’s laptop.

Gradle (and Maven) automate that graph. The important ideas:

| Idea | Meaning |
|------|---------|
| **Project** | This folder, named in `settings.gradle.kts` |
| **Build script** | `build.gradle.kts` — plugins, dependencies, Java version |
| **Plugin** | Extra tasks. `java` adds `compileJava`. Spring Boot plugin adds `bootRun` and the fat JAR |
| **Configuration** | A named classpath: `implementation` (compile + runtime), `testImplementation` (tests only) |
| **Repository** | Where JARs come from. Default is Maven Central |
| **Gradle installation** | The globally installed `gradle` command executes this project's build scripts |

---

## Why Gradle on this branch (not Maven)

You asked to use Gradle. That is enough as a team preference, but there are real differences.

| Topic | Gradle (this branch) | Maven (old branch) |
|-------|----------------------|--------------------|
| Build file | `build.gradle.kts` (code) | `pom.xml` (XML) |
| Build command in this project | Global `gradle` installation | Maven Wrapper (`mvnw.cmd`) |
| Incremental builds | Strong (task input/output snapshots) | Weaker historically |
| Spring Boot | Official plugin `org.springframework.boot` | `spring-boot-maven-plugin` |
| Dependency versions | Spring Boot plugin + `io.spring.dependency-management` **or** the Boot BOM | `spring-boot-starter-parent` POM |
| You already installed | Gradle 9.7.1 | Not required |

**Maven is not worse.** It is more declarative, more common in older Spring shops, and the parent POM is very simple. We are not claiming Gradle is “the Spring way.” Spring Initializr offers both. We chose Gradle because:

1. It is already on this machine at 9.7.1.
2. You want to learn one JVM build tool in depth.
3. Rebuilding the same POC with a different build tool makes the **application** the stable part and the **build** an explicit choice.

**Other alternatives we are not using**

| Tool | Why not here |
|------|----------------|
| Bazel | Excellent for huge monorepos; setup cost is absurd for a POC |
| Ant + Ivy | Historical; you would reinvent Gradle |
| IDE “just run Main” | Fine for a demo; not reproducible in CI |

---

## Groovy DSL vs Kotlin DSL

Gradle scripts can be:

- `build.gradle` — Groovy
- `build.gradle.kts` — Kotlin

We will use **Kotlin DSL**. Reasons:

- Spring Initializr default for new Gradle projects.
- The IDE can complete plugin IDs and dependency coordinates.
- Typos fail at configuration time more often.

Groovy is still everywhere in older samples. If a blog shows `implementation 'org.springframework.boot:...'` with single quotes, the Kotlin form is `implementation("org.springframework.boot:...")`.

---

## Why this project uses simple `gradle`

The Gradle Wrapper was initially generated, then removed at your request. This project now runs the globally installed executable:

```powershell
gradle test
gradle bootRun
```

This is simpler on this machine because Gradle 9.7.1 is already installed and its `bin` folder is on PATH.

The trade-off is reproducibility. The repository no longer pins or downloads Gradle automatically. A teammate or CI runner must:

1. Install Gradle separately.
2. Use a version compatible with the Spring Boot plugin.
3. Configure PATH correctly.

The **Gradle Wrapper** is the normal production recommendation because `gradlew` pins the build-tool version in the repository. It does not change application behavior and it is not a second build system. We are deliberately accepting the global-version requirement for this learning project.

---

## How a Spring Boot Gradle build will look (preview only)

When you prompt for the skeleton, expect something like this — **not in the repo yet**:

`settings.gradle.kts`

```kotlin
rootProject.name = "elasticsearch-growth-poc"
```

`build.gradle.kts` (shape, versions may change when we generate it)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.poc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

Unpacking that file is the real learning:

1. **`java` plugin** — compile, test, jar.
2. **`org.springframework.boot` plugin** — `bootRun`, `bootJar` (executable jar with Tomcat inside).
3. **`io.spring.dependency-management`** — reads Spring Boot’s BOM so you omit versions on `spring-boot-starter-*` lines. Maven people get the same thing from `spring-boot-starter-parent`.
4. **`toolchain { languageVersion = 21 }`** — Gradle can download a JDK if needed; we already have 21.
5. **`implementation` vs `testImplementation`** — test libraries do not ship in the production jar.

**Alternative:** Gradle’s built-in version catalogs (`gradle/libs.versions.toml`). Nice for many modules. Overkill for one POC until we feel pain.

**Alternative:** skip `dependency-management` plugin and write full versions on every line. Easy to drift (Jackson 2.15 with Boot that expects 2.17). We will use the plugin.

---

## Local Gradle cache vs “where did all those JARs go?”

The first `gradle build` downloads dependencies into:

```text
C:\Users\rajpu\.gradle\caches\
```

That is normal. It is the equivalent of Maven’s `C:\Users\rajpu\.m2\repository`. Do not commit `.gradle/` in the project (already in `.gitignore`).

---

## Commands to remember (after the skeleton exists)

```powershell
gradle tasks          # list what we can run
gradle classes        # compile main
gradle test           # tests
gradle bootRun        # start Spring Boot
gradle bootJar        # fat jar under build/libs
```

`gradle.properties` sets `org.gradle.console=plain`. Without that, Gradle draws a live **80% EXECUTING** bar that looks stuck. IntelliJ hides that UI and prints Spring logs. Plain console does the same in Cursor: Tomcat start, then each HTTP request, until you press Ctrl+C.

One-off equivalent:

```powershell
gradle bootRun --console=plain
```

Run `gradle -v` when diagnosing PATH or version problems.

---

## Common Windows failures (write new ones here as we hit them)

| Symptom | Cause | Fix |
|---------|--------|-----|
| `gradle` not recognized | `bin` not on PATH, or old terminal | Add `C:\gradle\gradle-9.7.1\bin`; new PowerShell |
| `JAVA_HOME is not set` | Gradle daemon cannot find JDK | Set User `JAVA_HOME` to Corretto 21 folder |
| Dependency download hangs | Corporate proxy / TLS inspection | Configure `gradle.properties` proxy (we will document if it happens) |
| Cursor terminal has no `git`/`java` | Cursor started before PATH change | Fully quit Cursor, reopen |

---

## What we will *not* do

- Convert the old Maven POC in place.
- Add Elasticsearch dependencies in the first Gradle prompt.
- Check in `build/` or `.gradle/`.
