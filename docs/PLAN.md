# Alisio · Plan de implementación

Launcher minimalista para Android, hecho a medida para un Galaxy S26 Ultra. Este documento es la fuente de verdad del proyecto: si una decisión cambia, se actualiza aquí antes que en el código.

## 1. Resumen

Alisio sustituye a One UI Home con tres ideas:

1. **Inicio fijo y casi vacío.** Reloj arriba; abajo, al alcance del pulgar: hueco *Ahora*, reproductor, dock y barra. Nada cambia de sitio solo.
2. **Una barra para todo.** Apps (por su nombre o parte de él), personas con su canal (WhatsApp o teléfono), accesos directos de apps, ajustes, cálculos, comandos y, si nada encaja, Claude.
3. **Local por diseño.** Sin cuentas, sin analítica y sin permiso de `INTERNET`.

Es para uso personal: se instala por adb y no se publica en Play Store por ahora.

## 2. Uso que cubre

| Uso | Apps | En Alisio |
| --- | --- | --- |
| Diario | Claude, YouTube, Spotify, Spark, X | Dock fijo: un toque |
| Frecuente | Apps de banco y la app web de gastos | Pila **Dinero** en el dock: dos toques |
| Cualquier otra | Maps, LinkedIn o la que sea | Barra: escribir el nombre |
| Personas | Llamada, WhatsApp y videollamada | Barra: nombre y después canal |
| Ocasional | Dos apps propias para un gimnasio, en desarrollo | Barra, *Ahora* tras instalar una versión nueva y zona Dev |
| Instalar | Play Store | Barra: «Buscar en Play Store» |

Hábitos que hay que respetar: minimalismo y abrir apps «dentro de otras» con gestos sobre un icono.

## 3. Decisiones

**Entra**, repartido en fases:

- Dock fijo de 5 apps más la pila Dinero.
- Barra universal con ranking por frecencia y tolerancia a erratas.
- Salidas de la barra: Preguntar a Claude, Buscar en la web y Buscar en Play Store. Enter sin resultados pregunta a Claude.
- Persona → canal, con el canal aprendido por contacto.
- Hueco *Ahora*: una sola sugerencia, siempre con su motivo.
- Reproductor mínimo y puntos de no leído, sin contadores.
- Gestos sobre los iconos del dock: deslizar ↑ abre una segunda app o acción; mantener abre el menú de la app.
- Zonas: vista de islas que sustituye al cajón de apps.
- Gasto rápido («12 café»), alarma y temporizador, y portapapeles al abrir la barra.

**Opcional:** doble toque para bloquear y deslizar ↓ para notificaciones (necesitan el servicio de accesibilidad), pausa antes de abrir X y YouTube, y una página «Hoy».

**Descartado:** perfiles que cambian el inicio entero, dock que se reordena solo, mapa o zoom como pantalla principal, inicio como línea de tiempo, resumen propio de notificaciones, widgets de Android, IA propia o en la nube, iconos que pierden color con el uso, limpieza de apps, conversión de divisas (pediría Internet), S Pen y AppFunctions (su permiso está reservado a apps del sistema).

## 4. Stack y estructura

- Kotlin, Jetpack Compose y Material 3 como base, con tema propio; coroutines y `StateFlow`.
- Gradle Kotlin DSL con version catalog (`gradle/libs.versions.toml`). AGP, Kotlin y Compose BOM en sus últimas versiones estables.
- `compileSdk` y `targetSdk`: el último estable instalado. `minSdk = 31`.
- `applicationId = "dev.jesusmolano.alisio"`.
- Un solo módulo `:app` al principio; separar módulos solo si el build lo pide.
- Inyección de dependencias manual: un `AppContainer` creado en `AlisioApp`.
- Room para historial y configuración; DataStore para preferencias simples.
- Fuentes **Inter** y **Space Grotesk** empaquetadas en `res/font` (licencia OFL), sin fuentes descargables.
- Formato con ktlint vía Spotless.
- Tests: JUnit para la lógica pura; tests de UI de Compose solo en flujos críticos.

```
app/src/main/java/dev/jesusmolano/alisio/
├── AlisioApp.kt              # Application y AppContainer
├── MainActivity.kt           # Única actividad, registrada como HOME
├── core/
│   ├── text/                 # Normalización: acentos y mayúsculas
│   ├── search/               # Matcher, ranking e índice en memoria
│   └── time/                 # Reloj inyectable para los tests
├── data/
│   ├── apps/                 # LauncherApps, iconos y accesos directos
│   ├── contacts/             # ContactsContract y filas de WhatsApp
│   ├── usage/                # UsageStatsManager
│   ├── media/                # NotificationListener y MediaSession
│   └── db/                   # Room: entidades y DAOs
├── feature/
│   ├── home/                 # Inicio, dock y pila Dinero
│   ├── search/               # Barra y resultados
│   ├── people/               # Tarjetas de persona y canales
│   ├── now/                  # Motor y tarjeta de Ahora
│   ├── zones/                # Zonas
│   ├── appmenu/              # Interior de app
│   └── onboarding/           # Rol HOME y permisos
└── ui/
    ├── theme/                # Colores, tipografía y formas
    └── components/           # Piezas reutilizables
```

Equivalencias si vienes de Vue: un composable es un componente, `StateFlow` hace de `ref`/`computed` y un `ViewModel` hace de store.

## 5. Arquitectura

Flujo: Android (LauncherApps, Contactos, UsageStats, NotificationListener, Bluetooth) → repositorios en `data/` → índice en memoria y ranking en `core/search` → un `ViewModel` por pantalla con `StateFlow` → pantallas Compose → intents a otras apps.

Alisio no trabaja en segundo plano. La única excepción es el `NotificationListenerService`, que enlaza el propio sistema.

### 5.1 Actividad de inicio

- `MainActivity` con `intent-filter` `MAIN` + `HOME` + `DEFAULT`, y también `LAUNCHER` mientras se desarrolla.
- Atributos de referencia, como en Launcher3: `launchMode="singleTask"`, `stateNotNeeded="true"`, `clearTaskOnLaunch="true"`, `resumeWhilePausing="true"` y `taskAffinity=""`.
- Edge-to-edge con barras del sistema transparentes. `windowSoftInputMode="adjustResize"` e `imePadding()` en la barra.
- Pulsar «inicio» estando ya en Alisio llega por `onNewIntent`: cierra la barra, Zonas y los menús.
- Atrás en el inicio no hace nada.
- Para ser el inicio: `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` desde el onboarding.

### 5.2 Apps

- Lista: `LauncherApps.getActivityList(null, user)` para cada perfil de `LauncherApps.profiles`.
- Cambios: `LauncherApps.Callback` para apps añadidas, quitadas o cambiadas. Guardar la hora de cada instalación o actualización para *Ahora* y, al arrancar, compararla con `PackageInfo.lastUpdateTime` por si el proceso estaba parado.
- Clave estable de cada app: `paquete/actividad#serie-de-usuario`.
- Abrir: `LauncherApps.startMainActivity` con `ActivityOptions.makeClipRevealAnimation` desde los límites del icono.
- Accesos directos: `LauncherApps.getShortcuts` con `FLAG_MATCH_DYNAMIC | FLAG_MATCH_MANIFEST | FLAG_MATCH_PINNED`, y abrirlos con `startShortcut`. Solo funciona siendo el launcher activo (`hasShortcutHostPermission()`).
- Información de la app: `LauncherApps.startAppDetailsActivity`.
- Iconos: el monocromo del `AdaptiveIconDrawable` (Android 13+) teñido con el color de texto; si la app no lo trae, su icono normal.
- Visibilidad de paquetes: `<queries>` con la intención `MAIN`/`LAUNCHER` y los paquetes o intents concretos que se usen (WhatsApp, Claude, `market://`, `tel:`, `smsto:` y búsqueda web). `QUERY_ALL_PACKAGES` solo si algo no aparece.

### 5.3 Barra y ranking

- Normalizar consulta y nombres: minúsculas y sin diacríticos (`java.text.Normalizer`, forma NFD).
- Calidad de la coincidencia: exacta 1,0 · empieza por 0,9 · inicio de palabra 0,8 · iniciales 0,7 · letras en orden 0,5 · una errata, solo con 4 letras o más, 0,4.
- Frecencia: `f = Σ 0,5^(días desde cada apertura / 7)`, con los eventos de los últimos 90 días.
- Extra horario: `h = 0,3` si hubo 3 aperturas o más en esa franja (±1 h) en los últimos 14 días.
- Puntuación final: `calidad × (1 + ln(1 + f) + h)`. En empate, primero el nombre más corto y después el orden alfabético.
- Todo en memoria: cada tecla recorre el índice entero. Objetivo: menos de 50 ms con 300 apps y 1000 contactos.
- Lista anclada abajo (`reverseLayout`), junto al teclado. El mejor resultado queda pegado a la barra y muestra «Enter».
- Tipos de resultado: apps, personas (fase 2), accesos directos, ajustes del sistema (lista fija de intents de `Settings` con nombres y sinónimos en español), cálculo y comandos (fase 5).
- Siempre visibles justo encima de la barra: «Preguntar a Claude», «Web» y «Play Store».
  - Claude: `ACTION_SEND` de tipo `text/plain` al paquete `com.anthropic.claude`; se oculta si no está instalado.
  - Web: `ACTION_WEB_SEARCH` con `SearchManager.QUERY`.
  - Play Store: `market://search?q=…&c=apps` y, si falla, la URL web de Play.
- Enter abre el primer resultado; si no hay ninguno, pregunta a Claude.
- Con la barra vacía: 3 personas y 4 apps recientes, y accesos a Zonas.

### 5.4 Personas (fase 2)

- `READ_CONTACTS`. Indexar nombre, foto pequeña, teléfonos y las filas de WhatsApp de `ContactsContract.Data`: tipos MIME `vnd.android.cursor.item/vnd.com.whatsapp.profile`, `vnd.android.cursor.item/vnd.com.whatsapp.voip.call` y `vnd.android.cursor.item/vnd.com.whatsapp.video.call`.
- Clave estable: `LOOKUP_KEY`. Reindexar con un `ContentObserver`.
- Canales:
  - Llamada: `ACTION_CALL` con `CALL_PHONE`; sin ese permiso, `ACTION_DIAL`.
  - SMS: `ACTION_SENDTO` con `smsto:`.
  - Chat, llamada o vídeo de WhatsApp: `ACTION_VIEW` sobre la fila de datos (`content://com.android.contacts/data/<id>`) con su tipo MIME y el paquete `com.whatsapp`.
- Canal aprendido: la misma frecencia, por par contacto-canal. El botón principal es el canal con más peso. Sin historial: chat de WhatsApp si existe la fila y, si no, llamada.
- Si WhatsApp cambia esas filas: abrir el chat con `https://wa.me/<número>` y ofrecer la llamada normal.

### 5.5 Inicio, Ahora, reproductor y puntos (fases 1 y 3)

- Dock: 5 huecos configurables y la pila Dinero, que abre una hoja con sus apps.
- Gestos de la fase 1: deslizar ↑ en una zona vacía o tocar la píldora abre la barra con el teclado.
- Reglas de *Ahora* (fase 3), de mayor a menor prioridad:
  1. App instalada o actualizada hace menos de 24 h, solo si tiene icono de inicio. Motivo: «Versión nueva instalada hace 2 h».
  2. Dispositivo de audio Bluetooth conectado: la app que más se abre en los 5 minutos siguientes a conectar ese dispositivo, con un mínimo de 3 casos. Motivo: «Coche conectado».
  3. App que se abre a esta hora (±30 min) en al menos 3 de los últimos 5 días del mismo tipo (laborable o fin de semana), que aún no se ha abierto hoy y que no está en el dock.
- Descartar oculta la sugerencia 6 h. Tres descartes de la misma regla y app la silencian para siempre.
- Reproductor: `NotificationListenerService` más `MediaSessionManager.getActiveSessions(componente)` y su listener. Muestra la sesión que suena (o la última) con pausa/reproducir y siguiente; un toque abre su app.
- Puntos de no leído: notificaciones activas por paquete, sin las permanentes, las de servicio en primer plano ni los resúmenes de grupo. Se desactivan por app; X viene desactivado.

### 5.6 Gestos sobre iconos e Interior de app (fase 3)

- Deslizar ↑ sobre un icono del dock abre su destino secundario: una app o un acceso directo configurable.
- Mantener abre una hoja con los accesos directos, el destino secundario («Cambiar»), el uso de hoy (`UsageStatsManager`), «Mover a otra zona» e «Información de la app».

### 5.7 Zonas (fase 4)

- Entrada: pellizcar el inicio. Salida: atrás o pellizcar hacia fuera.
- Islas por defecto: Diario (apps del dock), Dinero (la pila), Dev (asignadas a mano), Sistema (Ajustes y las apps predeterminadas de cámara, galería, reloj, teléfono y mensajes) y Resto (A–Z con índice lateral).
- Las islas no se mueven; dentro de cada una, orden por frecencia. Arrastrar una app a otra isla la reasigna.

### 5.8 Extras (fase 5)

- Gasto rápido: la expresión `^(\d+(?:[.,]\d{1,2})?)\s*€?\s+(.+)$` rellena una plantilla de URL configurable, por ejemplo `https://<tu-app>/nuevo?importe={importe}&concepto={concepto}`, que se abre con `ACTION_VIEW`. La app web de gastos tiene que aceptar esa ruta.
- Alarma: «alarma 7:30» → `AlarmClock.ACTION_SET_ALARM` con `EXTRA_HOUR`, `EXTRA_MINUTES` y `EXTRA_SKIP_UI`. Temporizador: «temporizador 10» → `AlarmClock.ACTION_SET_TIMER` con `EXTRA_LENGTH`. Permiso `SET_ALARM`.
- Portapapeles: leerlo solo al abrir la barra, porque Android 12 y posteriores avisan en cada lectura. Detectar URL, teléfono o dirección y ofrecer su acción.
- Accesibilidad opcional: un servicio mínimo que solo llama a `performGlobalAction` (`GLOBAL_ACTION_LOCK_SCREEN` y `GLOBAL_ACTION_NOTIFICATIONS`), con una pantalla de aviso propia antes de activarlo.

### 5.9 Datos (Room)

- `LaunchEvent`: tipo (app, acceso directo, contacto + canal o comando), clave, hora y dispositivo Bluetooth conectado si lo hay.
- `DockSlot`, `StackItem`, `IconGesture` y `ZoneAssignment` para la configuración.
- `PackageEvent`: instalación o actualización con su hora.
- `SuggestionFeedback`: regla, clave, número de descartes y último descarte.
- Al arrancar, borrar los eventos de más de 90 días.

## 6. Diseño

Maquetas de referencia, solo para ti (Claude Code no puede abrirlas): https://claude.ai/artifact/7zLw6haMBr2WDRjPrLyVkf

### Tokens

| Token | Valor |
| --- | --- |
| Fondo | `#0B0C0E` |
| Superficie | `#15171A` |
| Superficie elevada | `#1B1E22` |
| Borde | `#23272C` en tarjetas · `#2A2E34` en controles |
| Texto | `#F2F3F5` |
| Texto secundario | `#9AA0A8` |
| Texto terciario | `#8A9099` |
| Acento | `#6FD6C6` por defecto; alternativas `#8FB4FF`, `#F2B872` y `#C9A7FF` |
| Acento suave | el acento al 14 % de opacidad |
| Texto sobre acento | `#0B0C0E` |

| Elemento | Estilo |
| --- | --- |
| Reloj | Space Grotesk Light, 112 sp, interletrado −0,045 em |
| Título de pantalla | Space Grotesk Medium, 32 sp |
| Texto | Inter: 16 sp en títulos de fila (seminegrita), 15 sp en texto, 13 sp en secundario, 11,5 sp en etiquetas del dock |
| Etiqueta de sección | Inter seminegrita 11 sp, mayúsculas, interletrado 0,12 em, color terciario |
| Radios | tarjetas 22 dp · píldoras 28 dp · iconos del dock 17 dp (52 dp de lado) · filas 20 dp |
| Toque mínimo | 48 dp |
| Márgenes | 24 dp laterales en Inicio y 16 dp en la barra; rejilla de 8 dp |

### Pantallas

- **Inicio:** reloj y fecha arriba a la izquierda; tocarlos abre Reloj o Calendario. Espacio vacío. Abajo, en este orden: tarjeta *Ahora* (icono sobre acento suave, etiqueta «AHORA», título, motivo y botón circular para abrir), reproductor (carátula, título, app, pausa y siguiente), dock de 6 (5 apps y Dinero, con la etiqueta debajo y un punto de acento si hay algo sin leer) y la píldora de 56 dp «Busca o pregunta a Claude». Al pie, un brillo radial muy suave del acento.
- **Barra:** reloj atenuado arriba. Resultados apilados desde abajo por secciones (Personas, Acciones de apps y Apps), cada una con su etiqueta. El mejor resultado lleva borde de acento, fondo de acento suave y la marca «Enter». Encima de la barra, tres chips: Claude, Web y Play Store. Mientras escribes, la barra lleva borde de acento y botón de borrar, y las letras que coinciden van en acento.
- **Tarjeta de persona:** avatar con iniciales, nombre, motivo («Sueles usar WhatsApp») y botón principal relleno de acento con el canal aprendido. Debajo, chips con los demás canales.
- **Gasto rápido:** tarjeta de acento con importe, concepto y fecha, y el botón «Apuntar».
- **Preguntar a Claude:** tarjeta de acento con la pregunta citada y el botón «Preguntar». Debajo, «Buscar en la web» y «Buscar en Play Store».
- **Zonas:** título «Zonas» y la pista «Pellizca para volver». Islas como tarjetas en 2 columnas: Diario a todo el ancho, Dinero y Dev en media columna, Sistema a todo el ancho y Resto A–Z con índice lateral. Al pie: «Mantén una app y arrástrala para cambiarla de isla».
- **Interior de app:** inicio atenuado, el icono mantenido con borde de acento y una hoja flotante encima con: cabecera (icono, nombre y uso de hoy), «Accesos directos», «Deslizar ↑ sobre el icono» con su destino y «Cambiar», «Mover a otra zona» e «Información de la app».

Los textos de la interfaz van en español, en `strings.xml`.

## 7. Permisos

| Permiso | Fase | Para qué | ¿Hace falta? |
| --- | --- | --- | --- |
| Rol HOME | 0 | Ser el inicio y leer accesos directos | Sí |
| `READ_CONTACTS` | 2 | Personas en la barra | Para Personas |
| `CALL_PHONE` | 2 | Llamar con un toque | No; sin él se abre el marcador |
| Acceso a uso | 3 | Ranking horario, *Ahora* y uso de hoy | Recomendado |
| Acceso a notificaciones | 3 | Reproductor y puntos | Recomendado |
| `BLUETOOTH_CONNECT` | 3 | Saber qué audio está conectado | No |
| `SET_ALARM` | 5 | Comandos de alarma | Se concede al instalar |
| Accesibilidad | 5 | Bloquear y bajar notificaciones | No |
| `INTERNET` | — | — | **Nunca** |

Un task de Gradle debe fallar si el manifiesto final de debug o de release contiene `android.permission.INTERNET`.

## 8. Fases

Una fase cada vez. Al terminar cada una: compila, pasan los tests y se entrega una lista de pruebas manuales para el S26. No se empieza la siguiente sin mi visto bueno.

### Fase 0 · Prueba en el S26 (≈ 1 fin de semana)

- [x] Proyecto según §4, con Spotless/ktlint y el task anti-`INTERNET`.
- [x] `MainActivity` como HOME según §5.1 y onboarding mínimo con «Hacer predeterminado».
- [x] Lista A–Z de apps con icono y nombre; abrir con animación desde el icono.
- [x] Reacciona a instalaciones y desinstalaciones sin reiniciar.
- [x] `CLAUDE.md` del proyecto con los comandos de build, test, instalación y cómo fijarlo como inicio por adb.
- **Pruebas en el S26:** volver al inicio con el gesto desde varias apps; recientes; atrás; animación al abrir y al cerrar; tras reiniciar sigue siendo el inicio; sin tirones visibles.
- **Puerta:** si volver al inicio no es aceptable, el proyecto se para aquí.

### Fase 1 · Inicio y barra (≈ 2–3 semanas)

- [ ] Tema y tokens de §6, con las fuentes empaquetadas.
- [ ] Inicio: reloj, fecha, dock configurable de 5 apps, pila Dinero y píldora.
- [ ] Barra de §5.3 con apps, accesos directos, ajustes y cálculo.
- [ ] Salidas a Claude, web y Play Store, y el comportamiento de Enter.
- [ ] Room con `LaunchEvent` y ranking por frecencia.
- [ ] Tests de matcher, ranking, decaimiento, normalización y calculadora, y una prueba de rendimiento del índice con datos sintéticos.
- **Puerta:** una semana usándolo sin volver a One UI Home.

### Fase 2 · Personas (≈ 1–2 semanas)

- [ ] Índice de contactos y filas de WhatsApp (§5.4).
- [ ] Tarjeta de persona con botón principal y chips.
- [ ] Canal aprendido, con tests.
- **Puerta:** llamas a cualquiera sin abrir antes WhatsApp ni Teléfono.

### Fase 3 · Ahora, reproductor y gestos (≈ 2 semanas)

- [ ] Motor de *Ahora* con sus 3 reglas y tests con reloj inyectable.
- [ ] Reproductor y puntos de no leído.
- [ ] Deslizar ↑ sobre los iconos e Interior de app.
- **Puerta:** tocas más sugerencias de *Ahora* de las que descartas.

### Fase 4 · Zonas (≈ 2 semanas)

- [ ] Pellizco, islas, arrastrar entre islas e índice A–Z.
- **Puerta:** no echas de menos el cajón de One UI.

### Fase 5 · Extras (sin fecha)

- [ ] Gasto rápido, alarma y temporizador, y portapapeles.
- [ ] Opcionales: accesibilidad (bloquear y notificaciones) y pausa antes de X y YouTube.
- [ ] Pulido: Baseline Profile y medición del arranque.

## 9. Riesgos

| Riesgo | Mitigación |
| --- | --- |
| Volver al inicio con gestos puede verse menos fino que con One UI Home | Medirlo en la fase 0 antes de invertir más |
| WhatsApp puede cambiar las filas que añade a Contactos | Plan B con `wa.me` y la llamada normal |
| Compartir con Claude puede abrir la conversación actual en vez de una nueva | Probarlo en la fase 1; si pasa, copiar el texto y abrir Claude |
| One UI 8.5 no anima el gesto atrás en apps de terceros | Animaciones propias al cerrar la barra y Zonas |
| Cada actualización de One UI puede romper algo | Alcance pequeño y una prueba en el S26 tras cada actualización |

## 10. Reglas para trabajar en este repo

- Leer este plan antes de empezar cada fase y no salirse de su alcance.
- Si una API no se comporta en One UI como dice el plan, apuntarlo en §11 y preguntar antes de cambiar el enfoque.
- Ninguna dependencia nueva sin justificarla en el resumen de la fase, y ninguna que añada `INTERNET`.
- La lógica pura (ranking, parsers y reglas de *Ahora*) va sin dependencias de Android y con tests.
- Accesibilidad: `contentDescription` en todo lo que solo es un icono, y toque mínimo de 48 dp.

## 11. Notas durante el desarrollo

Aquí se apunta lo que se descubra al implementar: comportamientos de One UI, versiones y cambios de enfoque.

### Fase 0 (noviembre de 2026)

**Versiones fijadas** (últimas estables en ese momento): AGP 9.5.1, Gradle 9.9.1 (wrapper), Kotlin 2.4.21, Compose BOM 2026.09.00, ktlint 1.8.0 con Spotless 8.11.0, `compileSdk` 37.2 y `targetSdk` 37 (Android 17).

- AGP 9 trae Kotlin integrado: no se aplica el plugin `kotlin-android`, solo el del compilador de Compose. AGP 9.5 exige Gradle 9.
- `targetSdk 37` es el último estable del SDK, como pide §4, pero puede ir por delante del Android que lleve el S26 con One UI 8.5. No debería afectar; si algo se comporta raro en el teléfono, lo primero es probar con `targetSdk 36`. **Pendiente de confirmar en el dispositivo.**
- La variante release va con R8 y firmada con la clave de debug. Motivo: Compose en debug da tirones que no son reales, y la puerta de la fase 0 es justo la fluidez; así se instala por adb sin gestionar un keystore. Si algún día se publica, hay que cambiar la firma.
- El task anti-`INTERNET` (`checkNoInternetPermission`) lee el manifiesto ya fusionado de cada variante y cuelga de `check`, `assemble*` e `install*`.
- Dependencias: solo `core-ktx`, `activity-compose`, `lifecycle-*-compose`, Compose (UI, Foundation, Material 3) y JUnit. Ninguna añade permisos.
- Aplazado a su fase, aunque §5.2 lo describe: guardar la hora de instalación/actualización y compararla con `lastUpdateTime` (necesita Room; fase 3 con *Ahora*), accesos directos (fase 1), «Información de la app» (fase 3) y el resto de `<queries>` (se añaden con cada función). Room, DataStore y las fuentes tampoco entran aún: nada de la fase 0 los usa.
- El tema de la fase 0 solo lleva los colores de §6; tipografías y formas son de la fase 1.
- Los iconos siguen ya §5.2 (monocromo teñido sobre superficie si la app lo trae; si no, su icono normal), así que la lista mezcla ambos estilos.
- Al pulsar «inicio» estando en Alisio (`onNewIntent`), la lista vuelve arriba: es lo único que hay que «cerrar» en esta fase.
- `android:enableOnBackInvokedCallback` no se declara: con `targetSdk` 36 o más ya está activo.
- **Trazo (experimento, fuera del plan original, pedido expresamente).** Índice de letras en el borde derecho de la lista. Un solo gesto abre cualquier app sin teclado: tocar una letra, deslizar a la izquierda sobre la rejilla de apps de esa letra y soltar encima de una. Soltar sobre la letra solo lleva la lista hasta ella; soltar lejos de la rejilla cancela. Vibra al cambiar de letra y de app. La geometría (`TraceGrid`) y la agrupación por inicial (`toSections`) son lógica pura con tests. Si una letra tiene más apps de las que caben en pantalla, la rejilla enseña las primeras y el resto queda en la lista. **Por decidir tras probarlo:** si se queda, dónde vive a partir de la fase 1 (propuesta: borde derecho del Inicio, y la isla Resto de Zonas en vez del índice lateral simple de §5.7) y, en ese caso, pasarlo a §3 y §5.
- **Sin probar en dispositivo ni emulador**: el entorno donde se generó no tiene ninguno. Compila, pasan tests y lint, pero el comportamiento real en One UI (gesto de inicio, animaciones, rol HOME) solo lo dirán las pruebas manuales de la fase 0. → Probado después; ver el apartado siguiente.

### Fase 0 · Pruebas en el S26 (15 de noviembre de 2026)

Dispositivo: SM-S948B con **Android 17 (API 37) y One UI 9.0**, parche de octubre de 2026, navegación por gestos. El plan suponía One UI 8.5.

- `targetSdk 37` coincide con el sistema y no ha dado ningún problema; no ha hecho falta probar con 36.
- Rol HOME por adb: `cmd package set-home-activity` y `cmd role get-role-holders` funcionan tal cual en One UI 9. El inicio anterior del teléfono era Niagara (`bitpit.launcher`), no One UI Home.
- adb por cable: el teléfono salía como `unauthorized` hasta aceptar en él el diálogo «¿Permitir depuración por USB?» (el de la huella RSA; el de acceso a los datos del teléfono es otro). Si no aparece, revisar el Bloqueador automático de One UI, que bloquea los comandos por USB.
- Pruebas manuales superadas: volver al inicio con el gesto (limpio), recientes, atrás en el inicio (no hace nada), «inicio» estando en Alisio (la lista vuelve arriba), desplazamiento sin tirones y sigue siendo el inicio tras reiniciar. Arranque en frío por intent HOME: unos 70 ms. Sin errores de Alisio en logcat.
- **Al deslizar una app hacia arriba para ir al inicio se ve el fondo de pantalla** detrás de la app que se encoge, y se desvanece hasta el fondo `#0B0C0E`. Lo dibuja el gesto de inicio y recientes de One UI (`com.sec.android.app.launcher`, que sigue encargándose de ese gesto aunque no sea el inicio); Alisio no dibuja el fondo de pantalla. **Por decidir:** dejarlo así o enseñar el fondo de pantalla en el Inicio (`windowShowWallpaper`), lo que cambiaría el fondo fijo de §6.
- **La animación de apertura no sale del icono: no cuadra con §5.2.** One UI 9 descarta las animaciones de `ActivityOptions` al abrir una tarea nueva y usa la apertura por defecto (entra desde la derecha). Comprobado en el registro de transiciones (`WM_SHELL_TRANSITIONS`): el cambio llega sin opciones y carga `animAttr=0x8`. Probado con `makeClipRevealAnimation`, `makeScaleUpAnimation`, `makeThumbnailScaleUpAnimation` y `makeCustomAnimation`, por `LauncherApps.startMainActivity` y por `startActivity`: mismo resultado en las cinco. El código sigue con `makeClipRevealAnimation`, que no molesta. **Por decidir:** aceptar la apertura por defecto o animar dentro de Alisio (crecer desde el icono) antes de lanzar.
- Trazo: se activa al tocar, vibra (muy sutil), la rejilla sale a la altura del dedo, soltar abre la app correcta y las letras con más apps caben. El índice chocaba con el gesto atrás del borde derecho; arreglado con `Modifier.systemGestureExclusion()` sobre el índice. El tope de 200 dp por borde no se aplica a la app de inicio: `dumpsys window` muestra excluida la franja entera (unos 1423 px de alto). **Pendiente de confirmar a mano** que el gesto atrás ya no se cuela.
- Registro de transiciones para depurar animaciones: `adb shell cmd protolog_configuration logcat enable WM_SHELL_TRANSITIONS` y filtrar logcat por `WindowManagerShell`.

## 12. Referencias

- [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps): quién puede leer los accesos directos de otras apps.
- [MediaSessionManager](https://developer.android.com/reference/kotlin/android/media/session/MediaSessionManager): requisitos de `getActiveSessions`.
- [Gestión segura del portapapeles](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling): aviso al leer el portapapeles desde Android 12.
- [Llamada y vídeo de WhatsApp desde otra app](https://kaweah.freedombox.rocks/kiwix/stackoverflow.com_en_all_2023-11/questions/52875921/making-a-whatsapp-voice-video-call): Stack Overflow, copia archivada.
- [Uso de la API AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): los launchers no son herramientas de accesibilidad para Google Play.
- [One UI 2.5 y gestos con launchers de terceros](https://www.androidpolice.com/2020/08/18/samsungs-oneui-2-5-finally-lets-you-use-android-10s-gesture-navigation-in-third-party-launchers/): Android Police.
- [Vista previa del gesto atrás en One UI 8.5](https://www.sammobile.com/opinion/the-one-reason-i-am-not-looking-forward-to-one-ui-8-5/): SamMobile.
- [Permisos de AppFunctions](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/1c7f2811703d207e823972aceb4d93c56733005a): reservados a apps del sistema.
