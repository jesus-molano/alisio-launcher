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

Hábitos que hay que respetar: una interfaz limpia y abrir apps «dentro de otras» con gestos sobre un icono. «Limpia» no es minimalismo estricto: nada de iconos 3D ni fondos animados, pero sí bonita y actual, con el fondo de pantalla que yo elija.

## 3. Decisiones

**Entra**, repartido en fases:

- Dock fijo de 6 huecos. Cada hueco es una app o una pila: un grupo de apps con el nombre que yo le ponga (Dinero es un ejemplo, no algo fijo).
- Barra universal con ranking por frecencia y tolerancia a erratas.
- Salidas de la barra: Preguntar a Claude, Buscar en la web y Buscar en Play Store. Enter sin resultados pregunta a Claude.
- Persona → canal, con el canal aprendido por contacto.
- Hueco *Ahora*: una sola sugerencia, siempre con su motivo.
- Reproductor mínimo y puntos de no leído, sin contadores.
- Gestos sobre los iconos del dock: deslizar ↑ abre una segunda app o acción; mantener abre el menú de la app.
- Zonas: vista de islas que sustituye al cajón de apps.
- Gasto rápido («12 café»), alarma y temporizador, y portapapeles al abrir la barra.
- **Trazo:** índice de letras en el borde derecho del Inicio; tocar una letra, deslizar a la izquierda sobre sus apps y soltar encima de una la abre en un solo gesto (§5.5).

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

Alisio no trabaja en segundo plano: no arranca servicios propios ni programa trabajos. Las únicas excepciones son dos servicios que enlaza el propio sistema y solo si el usuario los activa: el `NotificationListenerService` (fase 3) y, desde la fase 5, el servicio de accesibilidad opcional (§5.8), que no recibe eventos y solo ejecuta dos órdenes. Mientras uno de ellos está activado, el sistema mantiene vivo el proceso de Alisio.

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
- Abrir: `LauncherApps.startMainActivity`. La animación es la de apertura por defecto del sistema: One UI 9 descarta las de `ActivityOptions` (§11), así que no se pide ninguna.
- Accesos directos: `LauncherApps.getShortcuts` con `FLAG_MATCH_DYNAMIC | FLAG_MATCH_MANIFEST | FLAG_MATCH_PINNED`, y abrirlos con `startShortcut`. Solo funciona siendo el launcher activo (`hasShortcutHostPermission()`).
- Información de la app: `LauncherApps.startAppDetailsActivity`.
- Iconos: el icono a color de cada app, todos con la misma forma (cuadrado redondeado). Los adaptativos se recortan a esa forma; los antiguos se encajan sobre la superficie elevada. Mezclar monocromos y de color quedaba desigual (§11).
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
  - Asistente de IA: `ACTION_SEND` de tipo `text/plain` al paquete elegido en los ajustes (por defecto `com.anthropic.claude`); se oculta si no está instalado.
  - Web: `ACTION_WEB_SEARCH` con `SearchManager.QUERY`, a la app elegida en los ajustes o a la del sistema.
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

- Dock: 6 huecos configurables. Tocar un hueco vacío o mantener pulsado cualquiera abre su hoja: elegir app (con buscador), convertirlo en pila o vaciarlo. Una pila tiene nombre editable y un toque abre una hoja con todas sus apps. Su icono se elige: carpeta (sus cuatro primeras apps en miniatura), el icono de la primera app o un símbolo o emoji propio.
- Gestos de la fase 1: deslizar ↑ en una zona vacía o tocar la píldora abre la barra con el teclado.
- Trazo en el borde derecho del Inicio: la rejilla de la letra sale a la altura del dedo, vibra al cambiar de letra y de app, y soltar lejos de la rejilla o sobre la letra cancela. El índice se excluye del gesto atrás del sistema. A partir de la fase 4 vive también en la isla Resto de Zonas.
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
| Fondo | El fondo de pantalla del sistema, oscurecido con un velo de `#0B0C0E` (más denso abajo) para que el texto se lea. `#0B0C0E` opaco en la barra; las hojas van sobre «Superficie» |
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

Los textos de la interfaz siguen el idioma del sistema: inglés por defecto (`values/strings.xml`) y español (`values-es/strings.xml`).

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
- **Superada el 15 de noviembre de 2026** (detalle en §11).
- **Pruebas en el S26:** volver al inicio con el gesto desde varias apps; recientes; atrás; animación al abrir y al cerrar; tras reiniciar sigue siendo el inicio; sin tirones visibles.
- **Puerta:** si volver al inicio no es aceptable, el proyecto se para aquí.

### Fase 1 · Inicio y barra (≈ 2–3 semanas)

Implementado el 15 de noviembre de 2026; las casillas se marcan al probarlo en el S26.

- [ ] Tema y tokens de §6, con las fuentes empaquetadas.
- [ ] Inicio: reloj, fecha, dock configurable de 6 huecos (apps o pilas), píldora y Trazo.
- [ ] Barra de §5.3 con apps, accesos directos, ajustes y cálculo.
- [ ] Salidas a Claude, web y Play Store, y el comportamiento de Enter.
- [x] Room con `LaunchEvent` y ranking por frecencia.
- [x] Tests de matcher, ranking, decaimiento, normalización y calculadora, y una prueba de rendimiento del índice con datos sintéticos.
- **Pruebas en el S26:** el fondo de pantalla se ve y el texto se lee; reloj y fecha abren Reloj y Calendario; configurar huecos del dock como app y como pila, renombrar una pila y buscar en su hoja; Trazo desde el Inicio; abrir la barra con la píldora y deslizando ↑; buscar apps con erratas, accesos directos, ajustes y una operación; Enter abre el primero y, sin resultados, pregunta a Claude; chips de Claude, Web y Play Store; atrás e «inicio» cierran la barra y las hojas; el orden mejora con el uso.
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

### Fase 6 · Revisión de experiencia y estética (añadida el 15 de noviembre de 2026)

Pedida expresamente para el final, con todo lo anterior ya construido.

- [ ] Revisar cada pantalla y cada flujo como usuario: que todo sea intuitivo, práctico y personalizable, sin perder funciones.
- [ ] Estética original y con buen gusto. Fuera lo genérico: cajas dentro de cajas, símbolos de relleno («✕», «≡», «⚙» escritos como texto), tarjetas por todas partes y cualquier diseño que parezca hecho en serie.
- [ ] Un lenguaje visual propio y coherente entre Inicio, barra, hojas y ajustes: jerarquía, ritmo, espacio, iconos dibujados y movimiento.
- **Puerta:** al usarlo no hay nada que parezca provisional ni de plantilla.

**Cómo se hacen las fases 3 a 6:** seguidas, sin esperar el visto bueno entre una y otra, con permiso para subir a la rama lo que vaya pasando sus comprobaciones. Lo que falle o no convenza se arregla después.

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
- Trazo: se activa al tocar, vibra (muy sutil), la rejilla sale a la altura del dedo, soltar abre la app correcta y las letras con más apps caben. El índice chocaba con el gesto atrás del borde derecho; arreglado con `Modifier.systemGestureExclusion()` sobre el índice. El tope de 200 dp por borde no se aplica a la app de inicio: `dumpsys window` muestra excluida la franja entera (unos 1423 px de alto). Confirmado a mano después: el gesto atrás ya no se cuela.
- Registro de transiciones para depurar animaciones: `adb shell cmd protolog_configuration logcat enable WM_SHELL_TRANSITIONS` y filtrar logcat por `WindowManagerShell`.

### Fase 1 (noviembre de 2026)

**Decidido al cerrar la fase 0:** se acepta la apertura por defecto de One UI (§5.2); el Inicio deja ver el fondo de pantalla con un velo (§6); Trazo se queda en el borde derecho del Inicio (§3, §5.5); la interfaz sigue el idioma del sistema, inglés por defecto y español; cualquier hueco del dock puede ser una pila con nombre propio; los iconos van a color con una sola forma.

**Versiones añadidas:** Room 2.8.5 con KSP 2.3.14. Justificación: el historial de aperturas y la configuración del dock (§4, §5.9). Ninguna añade permisos. Existe Room 3 (`androidx.room3`, 3.0.3 estable), pero cambia de paquete y de API; se queda la línea 2.8, que sigue mantenida. DataStore no entra todavía: nada lo usa.

- La lista A–Z de la fase 0 desaparece como pantalla: a cualquier app se llega por la barra o por Trazo. Soltar sobre la letra ya no desplaza nada; cancela, igual que soltar lejos de la rejilla.
- Fuentes: Inter y Space Grotesk como fuentes variables en `res/font` (un archivo por familia; cada peso se pide por el eje `wght`). Licencias OFL en `docs/licenses`.
- Base de datos: versión 3. Desde la 2 los cambios de esquema llevan migración (`MIGRATION_2_3` añade el icono de las pilas), para no borrar el dock ya configurado. Si faltara una migración, la base se borra y se empieza de cero (`fallbackToDestructiveMigration`).
- **Ajustes de Alisio.** Son una pantalla propia por secciones y crecen con cada fase: toda función nueva añade ahí lo que tenga de elegible. Se abren manteniendo pulsada una zona vacía del Inicio o buscándolos en la barra. Hoy tienen: Apariencia (color de acento entre los cuatro de §6 y cuánto se oscurece el fondo de pantalla), Búsqueda (asistente de IA y app de búsqueda web), Iconos (estilo o paquete) y Dock (enseñar o no los nombres). Las preferencias van en DataStore (`Prefs`).
- **Criterio de interfaz, pedido expresamente:** las opciones se eligen viendo el resultado, no leyendo una descripción. El icono de una pila se escoge entre tres vistas previas dibujadas con sus propias apps. Lo mismo vale para lo que venga: intuitivo y personalizable, sin quedarse en lo básico.
- **Asistente de IA elegible.** La barra ya no depende de Claude: pregunta a la app elegida en los ajustes (Claude por defecto) con `ACTION_SEND` de texto y su paquete. Al elegir salen primero los asistentes conocidos que estén instalados y después cualquier otra app. Si la elegida no está instalada, Enter sin resultados busca en la web.
- **Búsqueda web elegible.** Por defecto decide el sistema (`ACTION_WEB_SEARCH`). Se puede fijar una app que atienda ese intent o un navegador; a un navegador que no lo atiende se le manda la búsqueda como dirección de Google.
- Las apps de una pila se ordenan a mano: en su hoja, «En esta pila» las lista en su orden y se arrastran por el asa. Ese orden es el de la hoja y el del icono de carpeta.
- **Pendiente:** icono de pila a partir de una imagen; elegir tipografías; cambiar el icono de una app concreta.
- Esquema: `DockSlot` guarda una app o el nombre de una pila, y `StackItem` las apps de cada pila por hueco. `IconGesture` y `ZoneAssignment` llegan con sus fases.
- Barra: los accesos directos y los ajustes puntúan un poco menos que las apps (×0,85 y ×0,8; las conversaciones, ×0,95) para que, a igual coincidencia, gane la app. Límite por sección: 4 personas, 6 apps, 4 accesos directos y 3 ajustes. Las secciones se ordenan por su mejor resultado, de modo que el mejor de todos queda pegado a la barra.
- Los accesos directos se muestran con el icono de su app, no con el suyo: cargar el de cada uno al indexar era caro. Revisar si se echa de menos.
- Cálculo: si lo escrito es una operación, Enter copia el resultado al portapapeles.
- Sin resultados, Enter pregunta a Claude; si Claude no está instalado, busca en la web.
- Con la barra vacía se enseñan las 4 apps abiertas hace menos desde Alisio. Las personas llegan en la fase 2 y los accesos a Zonas en la 4.
- Los ajustes llevan sinónimos en español y en inglés para encontrarlos con el sistema en cualquiera de los dos idiomas.
- La frecencia se calcula al abrir la barra, no en cada tecla.
- Estilo de iconos elegible en los ajustes de Alisio (mantener pulsada una zona vacía del Inicio): a color con forma única (por defecto), monocromo (la capa temática de la app o, si no la trae, su icono en grises), del sistema (como los dibuja One UI) o un paquete de iconos instalado. Los paquetes son los de formato ADW/Nova (`appfilter.xml`); las apps que el paquete no cubre usan el estilo a color. La preferencia va en DataStore 1.3.0, la dependencia que §4 reservaba para preferencias simples. **Pendiente:** cambiar el icono de una app concreta.
- El reloj abre la app de Reloj por su icono de inicio. Lanzar `ACTION_SHOW_ALARMS` exige el permiso `SET_ALARM` (en One UI da `SecurityException`), así que ese intent solo se usa para averiguar qué app es el reloj.
- El texto de la barra vive en la pantalla y la búsqueda se hace en el momento, en cada tecla. Pasarlo por un flujo asíncrono hacía que el campo perdiera letras y el cursor, y que Enter abriera el resultado de la tecla anterior.
- Con la barra abierta se retira Trazo, y la barra se queda todos los toques: antes llegaban al Inicio de debajo. Deslizar hacia abajo cierra la barra.
- Un cambio de accesos directos solo relee los accesos directos. Releer las apps volvía a dibujar todos los iconos y cortaba un Trazo en curso con cada mensaje de una app de chat.
- Las hojas (dock, pila y ajustes) van sobre «Superficie» (`#15171A`); la barra, sobre `#0B0C0E` opaco.
- **Conocido, sin arreglar:** tras cambiar el idioma del sistema, los nombres de las apps y de los ajustes siguen en el idioma anterior hasta que se reinicia Alisio. (Arreglado en la fase 5.)
- **Sin probar a fondo en el dispositivo:** compila, pasan tests y lint y arranca sin errores en el S26, pero la barra y las hojas del dock no se han probado a mano todavía.

### Fase 2 (noviembre de 2026)

Empezada el 15 de noviembre de 2026 con el visto bueno para seguir, sin esperar la semana de uso de la puerta de la fase 1; la fase 1 se sigue afinando en paralelo.

- Permisos nuevos: `READ_CONTACTS` y `CALL_PHONE` (§7). Se piden juntos desde la barra vacía («Busca personas desde aquí») o desde los ajustes de Alisio. Sin el primero no hay personas; sin el segundo, llamar abre el marcador. `android.hardware.telephony` se declara como no obligatorio.
- Índice de contactos en memoria: nombre, teléfono principal (o el primero) y las filas de WhatsApp de cada contacto. Se relee cuando cambia la agenda, con una espera de 0,8 s porque una sincronización avisa muchas veces seguidas.
- **Sin confirmar en el S26:** que WhatsApp deja sus filas de chat, llamada y vídeo. Desde adb no se ven; el registro `ContactsRepository` escribe cuántas personas las tienen al leer la agenda. Si ningún contacto las tiene, el chat se ofrece a quien tenga teléfono y se abre con `wa.me`; llamada y vídeo de WhatsApp no se ofrecen. Si algún contacto las tiene, WhatsApp solo se ofrece a quien tenga la suya: no tenerla significa que esa persona no usa WhatsApp, y su canal por defecto es la llamada (§5.4).
- `wa.me` solo admite cifras: un número guardado sin prefijo de país puede no encontrar el chat.
- Una persona es un resultado más de la barra (sección Personas). Su fila lleva sus tres primeros canales como botones con icono: un toque contacta, sin pasos intermedios. El primero es el principal y va marcado con el acento. Tocar el nombre abre la tarjeta, con todos los canales, cada uno con su icono y su nombre. Pedido expresamente: iconos al lado del nombre en vez de pulsar Enter y elegir entre textos.
- Enter sobre una persona usa su canal principal, pero nunca marca solo: una llamada se queda en el marcador con el número puesto. Tocar el botón de llamar sí llama directamente si hay permiso.
- La tarjeta de una persona lleva un botón para abrir su ficha en la app de Contactos.
- Iconos de canal: dibujos de Material Symbols (Apache 2.0) en `res/drawable`. El chat de WhatsApp enseña el icono de la app; la llamada y el vídeo de WhatsApp usan el dibujo de teléfono y de cámara en su verde.
- Canal aprendido: cada contacto por un canal se apunta con la clave `person:<resumen del LOOKUP_KEY>|<canal>`. El botón principal es el canal con más frecencia; sin historial, chat de WhatsApp y, si no, llamada. La tarjeta solo dice «Sueles usar…» cuando el canal sale del historial.
- Las personas se ordenan con la suma de la frecencia de sus canales. Con la barra vacía salen las 3 contactadas hace menos, encima de las apps recientes.
- El avatar son las iniciales; la foto del contacto no se carga todavía. (Hecho en la fase 5.)
- **Personas unificadas.** Las apps de mensajería publican sus conversaciones como accesos directos, y salían en «Acciones de apps» por delante del contacto. Ahora una conversación que se llama igual que un contacto se funde con él (solo sale su tarjeta, que ya ofrece ese canal), y las demás conversaciones (un grupo, alguien sin guardar) van en Personas. «Acciones de apps» queda para las acciones de verdad.
- En el historial no se guarda el `LOOKUP_KEY` del contacto, sino un resumen SHA-256 recortado: en contactos locales esa clave lleva el nombre codificado, y el historial está en disco. Los contactos siguen solo en memoria.
- Si el permiso de contactos está denegado para siempre, pedirlo abre la ficha de Alisio en los ajustes del sistema.
- Ajustes de Alisio: sección Personas con el estado del permiso de contactos.
- **Sin probar a mano:** todo lo anterior. Compila, pasa tests y lint y está instalado, pero falta conceder el permiso en el teléfono y contactar a alguien por cada canal.

### Fase 3 (noviembre de 2026)

Empezada el 15 de noviembre de 2026. Se entrega por partes: primero Ahora; después el reproductor y los puntos de no leído; después los gestos sobre los iconos del dock y el Interior de app.

- **Ahora, primera parte.** El motor (`core/now`) tiene las tres reglas de §5.5 y los descartes, con tests y reloj inyectable. En el Inicio funcionan dos: versión nueva y costumbre. La de Bluetooth está en el motor pero aún no recibe datos: falta saber qué dispositivo de audio hay conectado y apuntar las aperturas que siguen a cada conexión, con el permiso `BLUETOOTH_CONNECT`.
- La hora de instalación o actualización sale de `PackageInfo.lastUpdateTime` al leer las apps. No hace falta la tabla `PackageEvent` de §5.9: ese dato sobrevive aunque el proceso estuviera parado.
- Una versión nueva deja de sugerirse en cuanto se abre la app desde Alisio. Las actualizaciones automáticas de Play también cuentan como versión nueva; tres descartes silencian esa app.
- La regla de costumbre usa el historial de aperturas hechas desde Alisio, no el acceso a uso del sistema: no pide permiso, pero no ve lo que se abre desde recientes o desde una notificación.
- La sugerencia se recalcula cada vez que el Inicio vuelve a verse. Descartes en la tabla `suggestion_feedback` (base de datos en la versión 4, con migración).
- Ajustes de Alisio: sección Ahora, con un interruptor para enseñar o no la sugerencia.
- Límites conocidos de la regla «versión nueva»: una app abierta desde Play Store tras instalarla sigue saliendo hasta abrirla desde Alisio; las apps actualizadas solas por la noche salen por la mañana y pasan por delante de la costumbre; y en apps que solo están en el perfil de trabajo no se sabe la hora de actualización. **Por decidir:** limitar la regla a las apps que yo marque.
- **Sin probar a mano.**
- **Ahora, regla de Bluetooth.** Ya recibe datos. `data/bluetooth/BluetoothAudio` sabe qué dispositivo de audio hay conectado: al arrancar consulta los perfiles A2DP, HEADSET y LE Audio, y después escucha sus avisos de conexión con un receptor de contexto. Sin servicio propio: solo se entera mientras el proceso vive. Ninguna dependencia nueva.
- Permiso `BLUETOOTH_CONNECT` (dispositivos cercanos), con `android.hardware.bluetooth` como no obligatorio. Se pide desde los ajustes de Alisio, sección Ahora, fila «Audio Bluetooth»; denegado para siempre, abre la ficha de la app, igual que los contactos. Sin permiso no hay dispositivo y la regla no aporta nada. Con permiso, la fila enseña el dispositivo conectado en ese momento. El permiso es el interruptor de la regla: no hay preferencia aparte en `Prefs`.
- Hora de conexión: la del aviso. Un dispositivo que ya estaba conectado cuando arrancó el proceso cuenta como conectado en ese arranque, porque el sistema no dice desde cuándo lo está. **Límite conocido:** si el sistema mata Alisio con el coche conectado, al volver se abre una ventana de 5 minutos que no corresponde a una conexión real.
- Las aperturas hechas desde Alisio en los 5 minutos siguientes a conectar guardan el nombre del dispositivo en `launch_event.bluetooth_device` (la columna ya existía: sin cambio de esquema ni migración). La ventana y el recuento son lógica pura en `core/now/BluetoothWindow.kt`, con tests.
- Un «caso» es una conexión, no un toque: dos aperturas de la misma app con el mismo dispositivo a menos de 5 minutos cuentan una vez. Solo cuentan las aperturas de apps, no las de accesos directos ni contactos.
- Se guarda el nombre que yo le haya puesto al dispositivo (alias) y, si no, el suyo. Renombrarlo empieza su historial de cero.
- Con varios dispositivos conectados cuenta el último en conectarse. Los de clase «wearable» (un reloj) se ignoran: enlazan el perfil de manos libres y, al reconectarse a cada rato, taparían al coche o a los cascos.
- El receptor se registra como exportado: los avisos de Bluetooth los manda la app de Bluetooth del sistema, que no corre con el UID del sistema, y a un receptor no exportado no le llegan. Son avisos protegidos; otra app no puede falsificarlos.
- `ItemRow` admite un dibujo vectorial (`leadingIcon`) en lugar del símbolo de texto; la fila de Bluetooth lo usa.
- **Sin probar en el dispositivo:** todo lo de Bluetooth. Falta conceder el permiso en el S26 y comprobar que llegan los avisos de conexión y desconexión en One UI 9, qué perfil enlazan los Buds (A2DP o LE Audio), que el reloj se descarta por su clase, y que tras tres conexiones con la misma app sale la sugerencia «… conectado».
- **Reproductor y puntos de no leído.** `data/media/AlisioNotificationListener` es el único servicio de Alisio; lo enlaza el sistema cuando concedo el acceso a notificaciones. `NotificationAccess` guarda si el acceso está concedido y el recuento por paquete; `MediaSessions` escucha las sesiones multimedia con `getActiveSessions(componente)` y su listener. Ninguna dependencia nueva y sin cambio de esquema.
- El acceso se concede en los ajustes del sistema. Los ajustes de Alisio tienen una sección Notificaciones: estado del acceso (un toque abre la pantalla de Alisio en «Acceso a notificaciones» y, si el sistema no la tiene, la lista general), un interruptor para el reproductor, otro para los puntos, y las apps del dock dibujadas con su punto para apagarlo una a una. Preferencias nuevas en `Prefs`: `playerEnabled`, `unreadDots` y `unreadMuted` (por defecto, `com.twitter.android`).
- Sin acceso no hay reproductor ni puntos, y nada falla: las llamadas que lo exigen capturan `SecurityException`. El acceso se vuelve a comprobar cada vez que el Inicio vuelve a verse.
- Reproductor: una sola pieza en forma de píldora entre Ahora y el dock, de 64 dp, más translúcida que la tarjeta de Ahora y sin borde. Carátula (o el icono de la app, o una nota), título, «artista · app», pausa o reproducir y siguiente. El botón de siguiente solo sale si la sesión lo admite. El índice de Trazo se acorta su alto.
- Qué sesión se enseña es lógica pura (`core/media/SessionChoice.kt`, con tests): la que suena; si ninguna, la última enseñada; si ya no existe, la primera del sistema. Una sesión que ni suena ni tiene título no cuenta.
- Tocar el reproductor abre la app por su icono de inicio (cuenta en el historial), no por la actividad que publica la sesión: lanzar ese `PendingIntent` choca con los límites de Android 14 y posteriores a los inicios de actividad, y no se ha podido probar. **Por decidir tras probarlo:** si se echa de menos que abra la pantalla del reproductor.
- La carátula se lee solo cuando cambian los metadatos y se reduce a 256 px: hay apps que publican su estado cada segundo.
- Puntos: «¿cuenta como no leída?» y el recuento por paquete son lógica pura (`core/notify/Unread.kt`, con tests). No cuentan las permanentes, las de servicio en primer plano ni los resúmenes de grupo. **Añadido al plan:** tampoco cuenta el aviso de un reproductor (categoría transporte o con sesión multimedia), que en pausa deja de ser permanente y dejaría un punto fijo en la app de música.
- El punto va en la esquina superior derecha del icono, sin contador. Una pila lo lleva si alguna de sus apps lo tiene. El recuento va por paquete: una app clonada en el perfil de trabajo comparte punto con la personal.
- Privacidad: de cada notificación solo se guarda, en memoria, su paquete y cuatro banderas. El servicio lleva la cuenta por clave de notificación y no pide la lista entera en cada aviso. Nada del contenido se guarda ni se escribe en el registro.
- **Sin probar en el dispositivo:** todo lo anterior. Falta conceder el acceso en el S26 y comprobar que One UI 9 abre la pantalla de detalle del acceso, que el reproductor sale con Spotify y YouTube y sus botones responden, que los puntos aparecen y se van con las notificaciones, que el aviso de un reproductor en pausa no deja punto, y el aspecto del reproductor y del punto sobre el fondo de pantalla.
- **Gestos sobre los iconos del dock e Interior de app.** Deslizar hacia arriba sobre una app del dock abre su destino secundario: otra app o un acceso directo de cualquier app. El icono con destino lleva un trazo corto de acento encima y acompaña un poco al dedo; al pasar de 40 dp vibra, y soltar ahí abre. Sin destino, el gesto abre la barra, como en una zona vacía. Las aperturas se apuntan en el historial como las demás (app o acceso directo). Ninguna dependencia nueva.
- Tabla `icon_gesture` (base de datos en la versión 5, con `MIGRATION_4_5`): el destino va **por app** (su `AppKey`), no por posición del dock. Al cambiar la app de un hueco, la nueva no hereda el gesto de la anterior, y la tabla servirá igual a los iconos de Zonas. Un acceso directo se guarda con paquete, id y serie de usuario. Un destino que deja de existir (app desinstalada, acceso directo dinámico que su app retira) no se borra: el icono se queda sin gesto hasta que vuelva.
- Mantener pulsada una app del dock ya no abre la hoja del hueco, sino el Interior de app (`feature/appmenu`): cabecera con icono, nombre y uso de hoy, y un botón redondo que abre la ficha de la app en el sistema; «Accesos directos» (hasta 6, las acciones antes que las conversaciones, cada uno con su icono propio, que se carga al abrir la hoja); «Deslizar hacia arriba sobre el icono» con el destino y «Cambiar», que pasa a un selector con buscador de apps y accesos directos; y «Hueco del dock» con tres acciones: cambiar la app (abre la hoja del hueco de siempre), convertir en pila y dejar vacío. Los huecos vacíos y las pilas siguen como estaban. «Mover a otra zona» llega con la fase 4.
- «Información de la app» es un botón en la cabecera y no una fila, para que la hoja no se alargue. El borde de acento en el icono mantenido (§6) no está hecho: el icono queda debajo del velo de la hoja y su forma depende del estilo de iconos.
- Uso de hoy: `data/usage/UsageAccess` suma el tiempo en primer plano desde la medianoche local con los eventos de actividad (`queryEvents`), no con las estadísticas agregadas, que van por tramos del sistema que no empiezan a medianoche. La suma y el texto («1 h 20 min», «35 min», «< 1 min») son lógica pura en `core/usage`, con tests. Solo se consulta al abrir la hoja. Las apps de otro perfil no enseñan uso: el sistema solo da el del perfil de Alisio.
- Acceso a uso: `PACKAGE_USAGE_STATS` declarado en el manifiesto; se concede en los ajustes del sistema. Sin él, la cabecera ofrece «Ver el uso de hoy», que abre esa pantalla, y nada falla. Se vuelve a comprobar cada vez que el Inicio vuelve a verse.
- Ajustes de Alisio, sección Dock: interruptor de los gestos (`Prefs.dockGestures`; apagado, el destino sigue guardado pero el icono no lo anuncia ni responde) y estado del acceso a uso con un toque para concederlo.
- TalkBack: el destino secundario es una acción con nombre del icono («Deslizar hacia arriba: abrir …»), porque el gesto no existe con el lector activo.
- **Sin probar en el dispositivo:** todo lo anterior. Falta comprobar en el S26 que el gesto sobre el icono le gana al que abre la barra y no dispara el toque ni la pulsación larga; que `ACTION_USAGE_ACCESS_SETTINGS` con el paquete abre la pantalla de Alisio en One UI 9 (si no, cae a la lista); que el uso de hoy cuadra con Bienestar digital; que los iconos de los accesos directos se ven bien; la migración de la versión 4 a la 5 sobre la base ya instalada; y el aspecto de la marca sobre el fondo de pantalla.
- **Correcciones tras la revisión de la fase 3.** La regla de Bluetooth ya no propone su app mientras dure la conexión: solo en los 15 minutos siguientes a conectar (`BLUETOOTH_SUGGEST_MILLIS`) y solo si la app no se ha abierto desde Alisio en esa conexión. Después pasa la regla de costumbre. Es una lectura de §5.5 regla 2; los 15 minutos son una elección mía, más larga que los 5 que cuentan como caso. **Por decidir tras usarlo:** si la ventana debe ser otra. La tarjeta no caduca sola con el Inicio a la vista: se retira en el siguiente recálculo.
- Con «Reproductor en el Inicio» apagado, `MediaSessions` no escucha las sesiones multimedia: escucha solo con el acceso concedido y el reproductor encendido (`AlisioSettings.playerEnabled`). Con el reproductor encendido sigue escuchando con Alisio fuera de la pantalla, como pide §5.5.
- Si el sistema rechaza la escucha de sesiones con el acceso ya concedido, se reintenta cada vez que el Inicio vuelve a verse (`MediaSessions.refresh`).
- El texto del uso de hoy sale de `strings.xml` (`usage_*`); `core/usage` solo parte el tiempo en horas y minutos (`usageDuration`).
- Al salir de los ajustes de Alisio a una pantalla del sistema (acceso a notificaciones, acceso a uso, ficha de la app), los ajustes siguen abiertos al volver, para ver el estado nuevo en la fila tocada. Vale para una sola salida.
- El nombre del dispositivo Bluetooth solo se guarda en las aperturas de apps. Las filas de contactos, ajustes y accesos directos ya guardadas con ese dato no se tocan: nadie las lee y caducan a los 90 días.
- Si falla un acceso directo, el registro solo escribe el paquete, no el id.
- El acceso a uso también cuenta como concedido con la operación en `MODE_DEFAULT` y el permiso `PACKAGE_USAGE_STATS` concedido (caso de `adb shell pm grant`).
- **No hecho, con su motivo:** vista previa para «Reproductor en el Inicio» y «Deslizar sobre los iconos del dock» (siguen siendo filas «Sí/No»; pide diseño nuevo y va a la fase 6); el borde de acento en el icono mantenido del Interior de app (cambio de diseño de §6, también para la fase 6); y la línea de estado de `CLAUDE.md`, que sigue diciendo «fase 2» y debe actualizarla el dueño del repo.
- **Sin probar en el dispositivo:** la ventana de 15 minutos con los Buds y el coche; que apagar y encender el reproductor en los ajustes quita y devuelve la píldora sin reiniciar; que los ajustes siguen abiertos al volver de las pantallas del sistema en One UI 9; y el reintento de la escucha de sesiones.

### Fase 4 (noviembre de 2026)

- **Zonas** (`feature/zones`) es una capa encima del Inicio, como la barra y los ajustes, y va debajo de ambos. Se abre pellizcando hacia dentro en el Inicio, desde la barra vacía (fila «Zonas», arriba del todo), buscándola («zonas», «islas», «cajón», «all apps»…) o desde los ajustes de Alisio. Se cierra con atrás, con «inicio», pellizcando hacia fuera o al abrir una app. Ninguna dependencia nueva ni permiso nuevo.
- Islas, en orden fijo: Diario (apps sueltas del dock), una por cada pila del dock con su nombre, Dev, las islas propias, Sistema y Resto. Dev, las pilas y las propias salen aunque estén vacías, porque son sitio donde soltar apps. Diario y Sistema vacías solo salen si a alguna app le tocan por defecto (ver «Correcciones tras la revisión»). Cada app está en una sola isla.
- **Lo asignado a mano gana a lo calculado**, también para una app del dock: se puede llevar Claude de Diario a Dev y sigue en el dock. A Diario y a las pilas no se entra desde Zonas (reflejan el dock): solo aceptan de vuelta a sus propias apps. A Dev, a las propias, a Sistema y a Resto se puede llevar cualquiera.
- Sistema se resuelve por intents (`data/apps/SystemApps`): Ajustes, cámara, galería, reloj y marcador, más los roles de teléfono y de SMS. Con varias candidatas y ninguna predeterminada vale la primera que venga con el teléfono. No ha hecho falta tocar `<queries>`: esas apps ya son visibles por tener icono de inicio.
- Orden dentro de cada isla: frecencia (`core/search/Frecency.kt`) calculada al abrir Zonas, no en vivo, para que nada se recoloque a la vista; en empate, A–Z. Resto va A–Z y lleva Trazo tal cual (`feature/home/Trace.kt`), con sus apps: el índice ocupa el borde derecho de toda la pantalla, como en el Inicio.
- Diseño: islas en filas de una o dos, cada una tan ancha como apps tiene (entre dos columnas y el ancho entero); una isla pequeña sola no se estira. Las islas son un fondo translúcido sin borde, con su nombre en etiqueta de sección; nada de tarjetas dentro de tarjetas. La lista es perezosa: cada fila de islas y cada fila de Resto es un elemento.
- Reasignar: mantener una app la coge (vibra), las islas a las que no puede ir se apagan y la que la recibiría se enciende; cerca del borde superior o inferior la lista se desplaza sola. Soltar fuera cancela. **Mantener y soltar sin mover abre el Interior de app**, que ahora lleva «Zona»: las islas posibles como chips, con la actual marcada. Es también la vía con TalkBack (acción «Mover a otra zona» de cada icono). Lo mismo sale en el Interior de app del dock.
- El arrastre se detecta en la pantalla y no en cada icono, porque la lista recicla sus filas al desplazarse; dónde está cada icono y cada isla se guarda fuera del estado de Compose.
- Islas propias: se crean con el botón «+» de Zonas o en los ajustes; tocar su nombre (o su fila en los ajustes) abre la hoja para renombrarla o borrarla, con un segundo toque para confirmar. Al borrar una isla sus apps vuelven a la isla que les toca sin asignación: Resto, salvo las del dock y las de Sistema. **Dev es fija**: no se renombra ni se borra. **Por decidir:** si debe ser una isla propia más.
- Datos: tablas `zone` (islas propias) y `zone_assignment` (app → isla, como `dev`, `system`, `rest` o `custom:<id>`); base de datos en la versión 6 con `MIGRATION_5_6`. Una asignación a una isla que ya no existe se ignora.
- Lógica pura con tests en `core/zones`: reparto y orden (`buildZones`, `ZoneResolver`), qué guardar al mover (`assignmentChange`, `moveTargets`), el empaquetado en filas (`zoneSpan`, `packZones`) y el umbral del pellizco (`pinchOf`).
- Ajustes de Alisio, sección Zonas: «Ver Zonas», «Pellizcar el Inicio» (`Prefs.zonesPinch`), «Nombres de las apps en Zonas» (`Prefs.zonesLabels`), las islas propias con su número de apps y «Nueva isla».
- El botón redondo de icono del Interior de app pasa a `ui/components` (`RoundIconButton`) y lo usa también Zonas. El pellizco es un modificador reutilizable (`Modifier.onPinch`).
- **No hecho:** reordenar las islas propias (salen en orden de creación); meter una app en Diario o en una pila soltándola encima (se hace desde el dock); letras de sección dentro de Resto (el índice es Trazo); vista previa para los dos interruptores de los ajustes (siguen siendo filas «Sí/No», como las de la fase 3).
- **Sin probar en el dispositivo:** todo lo anterior; compila y pasan tests y lint, nada más. Falta comprobar en el S26 que el pellizco se reconoce y no dispara la pulsación larga de los ajustes ni la barra; que coger una app le gana al desplazamiento de la lista y que soltarla no la abre; el desplazamiento solo junto a los bordes; que el índice de Trazo no estorba al gesto atrás ni a las islas de arriba; qué apps resuelve Sistema en One UI 9; la migración de la versión 5 a la 6 sobre la base ya instalada; la fluidez con todas las apps (en release); y el aspecto de las islas sobre el fondo de pantalla.

**Correcciones tras la revisión** (noviembre de 2026).

- **El desplazamiento automático espera a que el dedo se mueva.** Antes arrancaba en cuanto se cogía una app en la franja de 88 dp de arriba o de abajo: la lista subía otra isla bajo el dedo y soltar sin mover, que debe abrir el Interior de app, movía la app a esa isla. Ahora el bucle de `ZonesScreen` no desplaza hasta que `DragState.moved` es `true`.
- **Diario y Sistema vacías siguen a la vista mientras a alguna app le toquen por defecto** (`buildZones`). Si se llevaban todas las apps sueltas del dock a Dev, Diario desaparecía y con ella el único destino que borra la asignación (`AssignmentChange.Clear`); llevarlas a Resto guardaba «rest» para siempre. Ahora la isla vacía se queda como sitio donde soltar («Suelta apps aquí») y sale como chip en «Zona» del Interior de app, porque las dos vías parten de las islas pintadas. Sin apps sueltas en el dock, o sin apps de sistema resueltas, siguen sin salir. Test nuevo en `ZonesTest`.
- **Límite que queda:** si Sistema no tiene ninguna app por defecto ni asignada (no debería pasar: Ajustes siempre resuelve), no sale y no se puede llevar nada a ella. La isla vacía de Diario se apaga al arrastrar una app que no es del dock, como cualquier isla a la que no puede ir.
- **Zonas sigue abierta al volver de conceder el acceso a uso** desde el Interior de una app: `onStop` ya no la cierra cuando se sale a una pantalla del sistema (`leavingForSystemSettings`), igual que los ajustes.
- **No tocado: el orden por frecencia puede cambiar con Zonas ya a la vista.** `onOpened` recalcula frecencia y apps de sistema en otro hilo, y el primer fotograma usa el estado anterior; en la primera apertura del proceso, además, el historial puede no haberse leído aún. Arreglarlo bien pide retener el primer fotograma hasta tener el cálculo de esa apertura, que cambia cómo entra la pantalla; se deja para verlo en el dispositivo (puede no notarse dentro del fundido de entrada).
- **Pendiente de decisión del dueño:** la disposición de las islas no es la de §6 (Diario y Sistema a todo el ancho, Dinero y Dev a media columna): cada isla ocupa lo que piden sus apps. Se cambió sin preguntar; si se prefiere la de §6, es un cambio en `zoneSpan`/`packZones`. Los interruptores «Pellizcar el Inicio» y «Nombres de las apps en Zonas» siguen sin vista previa, en contra de la convención del repo; quedan para la fase 6.
- **Sin probar en el dispositivo:** nada de lo anterior; compila y pasan tests y lint.

### Fase 5 (noviembre de 2026)

- Hechos los extras de la barra de §5.8: gasto rápido, alarma y temporizador, y portapapeles. Sin dependencias nuevas, sin tablas nuevas y sin trabajo en segundo plano. La accesibilidad opcional, la pausa antes de X y YouTube y la medición del arranque se hicieron después, en esta misma fase (más abajo); el Baseline Profile no se añadió.
- Lógica pura con tests en `core/extras`: `parseExpense` y `expenseUrl` (gasto), `parseClockCommand` (alarma y temporizador) y `detectClip` (qué hay en el portapapeles).
- **Gasto rápido.** La expresión de §5.8 más una condición: el concepto tiene que llevar alguna letra («12 3» y «12 + 4» no son gastos). El importe viaja con punto decimal («12.50») y el concepto codificado con `%20`, no con `+`, para que la marca valga también en la ruta. La plantilla acepta `{importe}` y `{concepto}` y también `{amount}` y `{concept}`; vale con cualquier esquema (`https:` o el de una app) si lleva al menos una marca. Sin plantilla válida el resultado no existe.
- **Quién se queda Enter.** Una operación gana a todo («12 x 3» es una cuenta). La alarma y el temporizador empiezan por su palabra y se quedan Enter aunque haya resultados. El gasto cede ante cualquier resultado: solo es el destacado si la búsqueda no encuentra nada; si encuentra algo («3 en raya» con una app que coincida), su fila sale sin destacar justo detrás de la mejor sección. Cuando un extra se queda Enter, la fila «Preguntar a…» no sale (siguen los chips).
- **Alarma y temporizador.** Palabras: `alarma`, `alarm`, `despertador`, `temporizador`, `timer`, con relleno opcional («a las», «at», «de», «for»). Horas: `7:30`, `7.30`, `7h30`, `19:05`, `7`, `7h`, y `am`/`pm`. Duraciones: minutos por defecto, `10m`, `90s`, `1h`, `1h 30m`, `1h30`, `2m30`, palabras enteras («10 minutos»), y `1:30` (minutos:segundos) o `1:05:00`. Máximo un día, que es el límite de `EXTRA_LENGTH`. Se lanza con `EXTRA_SKIP_UI`, así que Alisio enseña un aviso propio («Alarma puesta a las 7:30») porque el reloj no abre su pantalla. Permiso `SET_ALARM` declarado.
- **Portapapeles.** Se lee una vez por apertura de la barra, cuando la ventana tiene el foco (sin él Android no deja leerlo). Antes mira la descripción, que no dispara el aviso del sistema: no lee si no es texto ni si viene marcado como sensible (lo que hacen los gestores de contraseñas). Si reconoce algo, con la barra vacía sale destacado junto a la barra y Enter lo abre: enlace → `ACTION_VIEW`, teléfono → marcador (`ACTION_DIAL`, nunca la llamada directa), dirección → `geo:0,0?q=`. Lo leído vive en el estado de la pantalla y se pierde al cerrarla.
- Qué reconoce `detectClip`: un enlace `http(s)` aunque venga dentro de una frase (se le quita la puntuación final), o un dominio suelto con `www.` o una terminación conocida; un teléfono si todo el texto son cifras y separadores (9–15 cifras, 8 con «+»); y una dirección postal por heurística (empieza por un tipo de vía y lleva número, forma inglesa «221B Baker Street», o código postal español entre palabras y comas). La dirección es una apuesta: prefiere no ofrecer nada.
- **Privacidad.** Ni el gasto ni lo copiado se apuntan en el historial ni en DataStore. `Outbound.open` tiene un modo `secret` que, si el intent falla, solo registra la acción y el tipo de excepción (el mensaje de la excepción lleva el intent entero).
- Ajustes de Alisio: en Búsqueda, «Acciones del portapapeles» (`Prefs.clipboardActions`, encendido por defecto); sección nueva «Gasto rápido» con el campo de la plantilla (`Prefs.expenseTemplate`) y, debajo, la dirección que abriría «12 café», que cambia mientras se escribe.
- Iconos nuevos en `res/drawable` (Material, Apache 2.0): `ic_alarm`, `ic_timer`, `ic_link`, `ic_place`, `ic_paste`, `ic_receipt`. El teléfono reutiliza `ic_channel_call`.
- **Dudas.** El portapapeles viene encendido, así que el aviso de Android sale en cada apertura de la barra si lo copiado viene de otra app; se apaga en los ajustes. Se podría leer solo cuando el clasificador del sistema (`ClipDescription.getConfidenceScore`) vea un enlace, un teléfono o una dirección, y ahorrar avisos; no se ha hecho porque no se sabe si One UI clasifica siempre. Enter con la barra vacía abre la acción del portapapeles (antes no hacía nada).
- **Sin probar en el dispositivo:** todo. Compila y pasan tests y lint. Falta comprobar en el S26: que el Reloj de Samsung atiende `ACTION_SET_ALARM` y `ACTION_SET_TIMER` con `EXTRA_SKIP_UI` sin abrir su pantalla (y si enseña su propio aviso, sobra el de Alisio); que el portapapeles se lee al abrir la barra y cuándo sale el aviso del sistema; que `geo:` abre el mapa con la dirección; que la plantilla abre la app web de gastos; y el aspecto de las filas nuevas y de los seis iconos, cuyos trazados están escritos de memoria.
- **Opcionales de §3, hechos después:** el servicio de accesibilidad con sus dos gestos y la pausa antes de abrir. Sin dependencias nuevas, sin permisos nuevos y sin cambio de esquema (todo va en DataStore). La medición del arranque está más abajo; queda sin hacer la página «Hoy».
- **Servicio de accesibilidad** (`data/accessibility`). `AlisioAccessibilityService` solo llama a `performGlobalAction`; su configuración (`res/xml/accessibility_service.xml`) no declara tipos de evento, lleva `canRetrieveWindowContent="false"` e `isAccessibilityTool="false"`. Es el segundo servicio de Alisio y, como el de notificaciones, lo enlaza el sistema: Alisio no lo arranca. `GlobalActions` (en `AppContainer`) guarda la instancia enlazada, dice si está activado (`AccessibilityManager.getEnabledAccessibilityServiceList`, se vuelve a mirar en `onResume`) y da las dos órdenes; sin servicio devuelven `false` y no pasa nada.
- **Gestos en el Inicio.** Dos toques en una zona vacía bloquean; deslizar hacia abajo baja las notificaciones. Solo se escuchan con el servicio activado y su interruptor encendido: sin eso, el detector de toques es el de antes (sin espera de segundo toque). El deslizamiento hacia abajo comparte detector con el que abre la barra: actúa al pasar de 48 dp, sin esperar a soltar, y ese gesto ya no abre la barra. Los dos toques van en el mismo detector que la pulsación larga de los ajustes; como el Inicio no tiene acción de un solo toque, no se retrasa nada. El pellizco sigue yendo antes y se queda los gestos de dos dedos; iconos, reloj y Trazo se quedan sus toques.
- **Aviso previo** (`feature/settings/AccessibilityNotice.kt`): pantalla propia con qué hace, qué no hace, por qué hace falta y cómo se activa, y un botón que abre `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Sale al tocar «Servicio de accesibilidad» en los ajustes con el servicio apagado; encendido, la fila va directa a los ajustes del sistema. Al volver, los ajustes de Alisio siguen abiertos con el estado nuevo.
- **Pausa antes de abrir.** `data/pause/LaunchPause` (en `AppContainer`) es el único paso por el que los tres `ViewModel` abren apps y accesos directos: dock, pilas, destino al deslizar, Ahora, reproductor, Trazo, barra y Zonas. Si el paquete lleva pausa, la apertura (y su apunte en el historial) queda pendiente y `feature/pause/PauseScreen` sale encima de todo: icono, cuenta atrás con la tipografía del reloj, «Abrir igualmente» (solo al llegar a cero) y «No abrir» (siempre, y el botón grande). Nada se abre solo. Atrás, «inicio» y salir de Alisio la descartan. Va por paquete: vale para los accesos directos de la app y para su copia en otro perfil.
- Lógica pura con tests en `core/pause`: `pauseBeforeOpening` (si una apertura lleva pausa), `pauseSecondsLeft` (cuenta atrás), `normalizePauseSeconds` y `pausedFirst` (orden de la lista de los ajustes).
- Preferencias nuevas en `Prefs`: `lockDoubleTap` y `notificationsSwipe` (encendidos; no hacen nada sin el servicio), `pausedApps` (por defecto `com.twitter.android` y `com.google.android.youtube`; vacío apaga la pausa) y `pauseSeconds` (3, 5 o 10; por defecto 5).
- Ajustes de Alisio: sección «Gestos del Inicio» (estado del servicio y los dos interruptores, que sin servicio dicen qué falta) y sección «Pausa» (las apps con pausa dibujadas con su icono, un toque la quita; lista con buscador para elegir varias, con las elegidas primero; duración en chips; y «Ver la pausa», que la enseña tal como saldrá sin abrir nada).
- Iconos nuevos (Material, Apache 2.0): `ic_lock`, `ic_swipe_down`, `ic_accessibility`, `ic_hourglass`.
- **Decisiones y dudas.** «Abrir igualmente» espera al final de la cuenta atrás en vez de abrir solo al acabar o estar disponible desde el principio. La pausa no cubre lo que no es abrir una app: los canales de una persona (WhatsApp, llamada), «Preguntar a…», la búsqueda web, Play Store, el reloj y el calendario del Inicio, ni el enlace del portapapeles. En la barra, una app con pausa cierra la barra y la pausa sale sobre el Inicio; en Zonas, Zonas sigue debajo. No hay periodo de gracia: reabrir la app al minuto vuelve a pausar. Los interruptores de los gestos vienen encendidos para que activar el servicio baste. Deslizar hacia abajo sobre un icono del dock también baja las notificaciones (el icono solo se queda el gesto hacia arriba cuando tiene destino). Con TalkBack los dos gestos no existen; no se han añadido acciones equivalentes porque el lector ya tiene las suyas.
- **Sin probar en el dispositivo:** todo. Compila y pasan tests y lint. Falta comprobar en el S26: que One UI 9 lista el servicio (en «Aplicaciones instaladas») y deja activarlo en una app instalada por adb, sin el bloqueo de «ajustes restringidos»; que `GLOBAL_ACTION_LOCK_SCREEN` bloquea sin romper el desbloqueo por huella y `GLOBAL_ACTION_NOTIFICATIONS` baja el panel; que los dos toques no chocan con la pulsación larga ni con el pellizco y que deslizar hacia abajo no se dispara al abrir la barra; que el estado de la fila cambia al volver de los ajustes del sistema; que la pausa sale desde todos los caminos y que el reloj de la cuenta atrás va fino en release; y los cuatro iconos, cuyos trazados están escritos de memoria.

**Pulido del arranque y pendientes pequeños** (hecho después, en la misma fase).

- **Cómo se midió.** Variante release instalada por adb en el S26 (103 apps con icono, 32 personas), con `am force-stop` y el intent HOME, sin enviar eventos de entrada. El teléfono estaba bloqueado y con la pantalla apagada, así que `am start -W` no dio `TotalTime` (la ventana no se dibuja detrás del bloqueo). Los números salen de marcas propias en logcat (`StartupTrace`, etiqueta `AlisioStartup`): milisegundos desde que el sistema crea el proceso. Se quedan en el código; para repetir la medida, `adb logcat -s AlisioStartup`.
- **Resultado** (6 arranques, muy estables): `Application.onCreate` empieza a los 6–7 ms; el `AppContainer` entero se crea en unos 6 ms en el hilo principal (lo más caro es `BluetoothAudio`, 1,4 ms; Room, DataStore y el resto están por debajo de 1 ms cada uno); la actividad está creada a los 16–21 ms; el primer fotograma con las preferencias ya leídas llega a los 50–60 ms; y las apps con sus iconos, a los ~500 ms. Con la pantalla apagada, «primer fotograma» es el primer pulso del `Choreographer`, no un fotograma en el panel, y no siempre sale.
- **`AppContainer`: no se ha movido nada.** No hay nada que retrasar: 6 ms en total y ninguna lectura de disco ni llamada lenta en el hilo principal (Room abre la base en la primera consulta, DataStore lee en su hilo, los iconos se cargan en `Dispatchers.IO`).
- **Iconos: se probó repartirlos entre hilos y se descartó.** Lo único lento del arranque es leer y dibujar los 103 iconos (~460 ms la primera vez). Repartido por paquete entre 2, 4 y 8 hilos tardó lo mismo, medido dentro del mismo proceso: una segunda lectura completa tarda ~92 ms con cualquier número de hilos, y la primera ~500 ms con 4 y con 8. El coste está en la primera vez que el proceso lee cada app, y no se reparte. El código sigue de uno en uno, con un comentario que lo explica. (Una tanda con 4 hilos dio ~340 ms, pero no se repitió con el mismo APK: fue el estado del teléfono, no el cambio.)
- **Baseline Profile: no se añade.** Motivos: (1) el primer fotograma ya llega a los ~55 ms del proceso, y la fase 0 midió ~70 ms de `TotalTime`; (2) el APK de release ya lleva los perfiles de las bibliotecas (Compose y demás), AGP los empaqueta sin configurar nada y `installRelease` los instala con su `.dm`: `dumpsys package` dice `status=speed-profile, reason=install-dm`; `profileinstaller` ya viene como dependencia transitiva; (3) un perfil propio solo cubriría el código de Alisio y exige un módulo de macrobenchmark y más dependencias; (4) lo que tarda (los iconos) es lectura de recursos de otras apps, que un perfil no acelera; (5) con el acceso a notificaciones concedido, el sistema vuelve a crear el proceso él solo en cuanto muere (se ve en logcat: `Start proc … bound-service … AlisioNotificationListener`), así que un arranque en frío a la vista es raro.
- **Si algún día molesta ver el dock sin iconos medio segundo:** lo que lo arreglaría es guardar en disco los iconos ya dibujados (por app, versión y estilo), pintarlos al arrancar y refrescarlos después. No se ha hecho: cambia el comportamiento (un icono vivo como el del calendario saldría un instante con el día anterior) y no es un cambio pequeño.
- **Idioma del sistema.** `AlisioApp.onConfigurationChanged` avisa al `AppContainer`, que compara los idiomas (`locales`, un `StateFlow`) y, solo si han cambiado, relee las apps (`AppsRepository.reload`). `SearchViewModel` ya no recibe los nombres de los ajustes resueltos: los pide con el contexto de la aplicación cada vez que `locales` cambia. El resto de textos se resuelve al componer y ya seguía el idioma al recrearse la actividad.
- **Foto del contacto.** `Person` lleva `photoUri` (`PHOTO_THUMBNAIL_URI`, en la misma consulta de la agenda, sin coste extra). `data/contacts/ContactPhotos` lee cada miniatura en `Dispatchers.IO` solo cuando una fila o la tarjeta la pinta, la recorta en círculo y la guarda en una caché en memoria de 64, que se vacía al releer la agenda. `rememberContactPhoto` (en `feature/people`) devuelve la foto o `null`; mientras tanto, y si no hay foto o falla la lectura, se ven las iniciales. Sale en las filas de personas de la barra (resultados y recientes) y en el avatar de la tarjeta. Opción nueva `Prefs.contactPhotos` («Fotos de los contactos», encendida) en la sección Personas de los ajustes; sin permiso de contactos la fila dice qué falta y no se marca. Nada va a disco.
- **Decisiones.** En las filas la foto va en círculo y ocupa el sitio del recuadro de iniciales (que es un cuadrado redondeado): se reutiliza `ItemRow(icon = …)` y `AppIcon` sin tocarlos. Se usa la miniatura (96 px en Android de serie) también en la tarjeta, donde el avatar mide 56 dp y puede verse algo blanda; la foto grande queda para la fase 6 si hace falta. Las conversaciones que no son de la agenda (accesos directos) siguen con el icono de su app.
- **Instalado en el teléfono:** la release de este árbol (con Zonas y los extras de la fase 5), para medir. La migración 5→6 de Room pasó sobre la base real sin errores en logcat y Alisio sigue siendo el inicio.
- **Sin probar en el dispositivo** (el teléfono estuvo bloqueado y no se envían eventos): `TotalTime` de `am start -W` con la pantalla encendida; que tras cambiar el idioma del sistema cambian los nombres de las apps y de los ajustes en la barra sin reiniciar; y las fotos en la barra y en la tarjeta (solo se ha visto que la consulta con la columna nueva lee las 32 personas sin error).

**Correcciones tras la revisión** (noviembre de 2026).

- **Direcciones del portapapeles.** Tras el tipo de vía hace falta un espacio, salvo en las abreviaturas que acaban en «/» o «.» («c/Mayor 5»). Antes «Plazas libres para el 15» o «Placas solares 3 kW» pasaban por direcciones y Enter abría el mapa. Tests nuevos en `ClipContentTest`.
- **Aviso de plantilla inválida.** Decía que tenía que empezar por `https://`, pero `isExpenseTemplate` acepta cualquier esquema; ahora pide «una dirección completa (como https://…)» y una marca.
- **Aviso de accesibilidad.** Ya no promete que «todo lo demás funciona igual»: dice que el resto de Alisio funciona igual y que algunas apps, como las de banco, miran si hay servicios de accesibilidad activos y pueden avisar. **Sin comprobar** con las apps de banco del teléfono.
- **Gesto sin respuesta.** Si el servicio consta como activado pero no está enlazado, `GlobalActions` devuelve `false`; el Inicio enseña ahora un aviso breve («El servicio de accesibilidad no ha respondido») en vez de no hacer nada. No se sabe cuánto dura esa ventana en One UI 9.
- §5 ya nombra el servicio de accesibilidad como segunda excepción al «no segundo plano».
- **No tocado, pendiente de decisión del dueño:**
  - **Baseline Profile.** §8 lo pide y se decidió no añadirlo sin preguntar (motivos y medidas arriba). La casilla de §8 sigue sin marcar hasta que el dueño lo acepte o lo pida.
  - **Gasto rápido frente a §6.** §6 pide una tarjeta de acento con importe, concepto, fecha y el botón «Apuntar»; lo hecho es una fila `ItemRow` («Apuntar 12 € · café», con la dirección debajo), sin fecha ni botón. La fila es coherente con el resto de resultados de la barra; la tarjeta queda para la fase 6 si se prefiere.
  - **Enter con la barra vacía abre lo copiado.** Android solo limita leer el portapapeles, no escribirlo: otra app puede dejar ahí un enlace y un Enter por costumbre lo abriría (`ACTION_VIEW`; el teléfono solo abre el marcador). El riesgo es bajo y la fila sale destacada, pero es una decisión: la alternativa es que lo copiado solo se abra tocando su fila.
  - **Pausa y enlaces.** Un enlace de YouTube o de X copiado se abre en su app sin pasar por la pausa (ya apuntado en «Decisiones y dudas»). Cubrirlo pide resolver qué app atenderá el enlace antes de abrirlo.
  - **Opciones sin vista previa.** `clipboardActions`, `contactPhotos`, `lockDoubleTap` y `notificationsSwipe` son filas «Sí/No»; la convención pide ver el resultado. Queda para la fase 6.
  - **`CLAUDE.md` del repo** sigue diciendo «Estado: fase 2… No adelantar trabajo de fases posteriores», con las fases 3 a 5 ya implementadas (las autoriza §8). No se ha tocado: es un archivo de instrucciones y lo cambia el dueño.

## 12. Referencias

- [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps): quién puede leer los accesos directos de otras apps.
- [MediaSessionManager](https://developer.android.com/reference/kotlin/android/media/session/MediaSessionManager): requisitos de `getActiveSessions`.
- [Gestión segura del portapapeles](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling): aviso al leer el portapapeles desde Android 12.
- [Llamada y vídeo de WhatsApp desde otra app](https://kaweah.freedombox.rocks/kiwix/stackoverflow.com_en_all_2023-11/questions/52875921/making-a-whatsapp-voice-video-call): Stack Overflow, copia archivada.
- [Uso de la API AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): los launchers no son herramientas de accesibilidad para Google Play.
- [One UI 2.5 y gestos con launchers de terceros](https://www.androidpolice.com/2020/08/18/samsungs-oneui-2-5-finally-lets-you-use-android-10s-gesture-navigation-in-third-party-launchers/): Android Police.
- [Vista previa del gesto atrás en One UI 8.5](https://www.sammobile.com/opinion/the-one-reason-i-am-not-looking-forward-to-one-ui-8-5/): SamMobile.
- [Permisos de AppFunctions](https://gitlab.e.foundation/e/os/android_frameworks_base/-/commit/1c7f2811703d207e823972aceb4d93c56733005a): reservados a apps del sistema.
