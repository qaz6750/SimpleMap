# SimpleMap workspace instructions

- Use Kotlin, Jetpack Compose, Material 3, and Gradle Kotlin DSL.
- Keep `com.simplemap` as the application ID and namespace.
- Use JDK 17, minSdk 26, and compile/target SDK 36.
- Never commit AMap keys, signing files, location traces, or user data.
- Do not call any AMap SDK API before explicit privacy consent is persisted.
- The AMap navigation SDK includes map, location, and search; do not add duplicate AMap SDK artifacts.
- Keep AMap Android Views behind lifecycle-aware Compose adapters.
- Prefer immutable UI state, unidirectional data flow, and focused feature packages.
- Draw app-specific icons with Compose paths or vector drawables; preserve accessibility semantics.
- Run unit tests, lint, and a debug build before each functional commit.
