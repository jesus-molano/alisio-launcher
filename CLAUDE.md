# Alisio

Launcher Android personal para un Galaxy S26 Ultra. **Lee `docs/PLAN.md` antes de tocar nada**: es la fuente de verdad (alcance por fases, arquitectura, diseño y reglas de §10). Lo que se descubra al implementar va a su §11.

Estado: fase 0 (lista A–Z como HOME). No adelantar trabajo de fases posteriores.

## Requisitos

- JDK 21 y Android SDK con `platforms;android-37.2` y `build-tools;36.0.0` (`ANDROID_HOME` o `local.properties` con `sdk.dir`).
- Usar siempre `./gradlew` (Gradle 9.9.1; AGP 9.5 no funciona con Gradle 8).

## Comandos

```bash
./gradlew assembleDebug                # APK de debug → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease              # APK optimizado (R8), firmado con la clave de debug
./gradlew testDebugUnitTest            # tests JUnit
./gradlew lintDebug                    # lint de Android
./gradlew spotlessApply                # formatea con ktlint (spotlessCheck solo comprueba)
./gradlew checkNoInternetPermission    # falla si el manifiesto final de debug o release pide INTERNET
./gradlew spotlessCheck check          # todo lo anterior de una vez (check incluye tests, lint y anti-INTERNET)
```

`assemble*` e `install*` dependen del task anti-INTERNET de su variante: no se puede generar un APK que lo pida.

## Instalar por adb

```bash
./gradlew installDebug                 # o installRelease
# o a mano:
adb install -r app/build/outputs/apk/release/app-release.apk
```

Debug y release comparten `applicationId` y firma, así que una sustituye a la otra sin desinstalar. Para juzgar fluidez (tirones, animaciones) usar **release**: Compose en debug va notablemente peor.

Depuración inalámbrica (móvil y ordenador en la misma Wi‑Fi):

1. Ajustes → Opciones de desarrollador → Depuración inalámbrica → activar.
2. «Vincular dispositivo con código de vinculación» → `adb pair <ip>:<puerto-de-vinculación>` y escribir el código (solo la primera vez).
3. `adb connect <ip>:<puerto>` con el puerto de la pantalla principal de Depuración inalámbrica (no es el de vinculación y cambia cada vez).
4. `adb devices` debe listar el S26.

## Fijarlo como inicio

```bash
adb shell cmd package set-home-activity dev.jesusmolano.alisio/.MainActivity
# comprobar:
adb shell cmd role get-role-holders android.app.role.HOME
# volver al inicio anterior (Niagara; One UI Home es com.sec.android.app.launcher):
adb shell cmd role add-role-holder android.app.role.HOME bitpit.launcher
```

Verificado en el S26 con One UI 9.0 (Android 17). Si `adb devices` muestra `unauthorized`, falta aceptar en el teléfono «¿Permitir depuración por USB?»; si el diálogo no sale, revisar el Bloqueador automático de One UI.

También desde la app («Hacer predeterminado») o en Ajustes → Aplicaciones → Elegir aplicaciones predeterminadas → Aplicación de inicio.

Útiles: `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` (simula «inicio») y `adb logcat --pid=$(adb shell pidof dev.jesusmolano.alisio)`.

## Convenciones

- Un módulo `:app`; paquetes `core/` (lógica pura, sin Android, con tests), `data/`, `feature/`, `ui/` según §4 del plan.
- DI manual: `AppContainer` en `AlisioApp`. Un `ViewModel` por pantalla con `StateFlow`.
- Versiones solo en `gradle/libs.versions.toml`. Ninguna dependencia nueva sin justificarla, y ninguna que añada `INTERNET`.
- Textos de interfaz en español en `res/values/strings.xml`; comentarios y mensajes de commit en español.
- `contentDescription` en todo lo que sea solo icono y toque mínimo de 48 dp.
- Antes de dar algo por terminado: `./gradlew spotlessCheck check assembleDebug`.
