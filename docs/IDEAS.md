# Alisio, fase 6: ideas para que sea propio

## 0. Pedido por el dueño (16 de noviembre de 2026, tras probar la fase 6 en el S26)

Esto va antes que las ideas de abajo. El dueño aún no ha elegido cuáles de ellas se hacen.

- **Ajustes abiertos desde Zonas:** se ven mal. Hay que reproducirlo (abrir los ajustes desde el engranaje de Zonas) y arreglarlo. Es un fallo, no una idea.
- **Trazo, revisión completa de uso y estética.** Lo que pide:
  - **Alcance:** con el reproductor y Ahora a la vista, las letras quedan arriba a la derecha, y con apps de todo el abecedario llegar a la «A» es incómodo. El índice tiene que quedar al alcance del pulgar: desplazable, o comprimido, o con otra solución mejor.
  - **Ocultable:** que se pueda esconder (opción en los ajustes, con vista previa).
  - **Sin errores:** que no se abra nada sin querer y que las letras y las apps no estén tan juntas que lleven a equivocarse.
  - **Animaciones y sensación:** revisarlas, y tomar como referencia los detalles de Niagara.

## 1. Lo que haría de Alisio algo propio

Alisio no tiene que ganar por tener más funciones. Tiene que ganar porque cada pieza hace una sola cosa, dice por qué la hace y se siente igual cada vez que se usa. Su seña es esta: **un Inicio tranquilo que obedece al sistema, explica lo que aprende y deja corregirlo en el sitio donde se ve, sin que nada salga del teléfono**. El pulido tiene tres partes: el tacto (una háptica con significado fijo), el movimiento (transiciones propias que siguen al dedo) y la fiabilidad (el primer toque y el teclado nunca fallan). Las funciones nuevas solo entran si refuerzan una de estas tres partes o si su falta parece un defecto.

## 2. Recomendadas

El orden va de más valor por esfuerzo a menos. Todas pasan las dos críticas, a veces con cambios. Ninguna pide INTERNET ni trabajo en segundo plano.

### 2.1 Vocabulario háptico (A10)
- **Qué es:** un objeto `AlisioHaptics` junto a `AlisioMotion`, con tres significados fijos. «Paso» es cambiar de letra o de app en Trazo. «Hecho» es Enter, poner en el dock o soltar en una isla. «Límite» es una acción imposible o llegar al final. El umbral del deslizar sobre el dock vibra al pasar y al volver atrás. Los interruptores usan encendido y apagado.
- **Cómo se usa:** no se configura. Se nota en cada gesto.
- **Por qué la distingue:** casi ningún launcher usa las constantes semánticas de API 34, y la mayoría vibra igual para todo. Se nota cien veces al día. Se documenta en DISENO §2 como un token.
- **Permisos:** ninguno. **Esfuerzo:** pequeño.
- **Objeción y solución:** con `minSdk` 31, las constantes de API 34 (`SEGMENT_TICK`, `GESTURE_THRESHOLD_ACTIVATE` y `DEACTIVATE`, `DRAG_START`, `TOGGLE_ON` y `TOGGLE_OFF`) van protegidas con una comprobación de versión, y `CLOCK_TICK` queda de respaldo. One UI traduce las constantes a su motor háptico, así que hay que probar en el S26 que suenan distintas. Si dos suenan igual, cada una lleva su alternativa. Hoy hay cinco llamadas repartidas entre HomeScreen, Trace y ZonesScreen, y se reúnen en el objeto.

### 2.2 Atrás que sigue al dedo (A14)
- **Qué es:** al empezar el gesto atrás, la barra, Zonas o una hoja bajan hasta 12 dp y pierden alfa según el progreso. Si se suelta antes del final, vuelven a su sitio. Si el gesto acaba, se cierran con Exit.
- **Por qué la distingue:** es el punto débil de los launchers de terceros en One UI (Lawnchair, Niagara), y es la mitigación que §9 ya pide. Alisio pule lo que sí controla. Cumple DISENO §2.7: sin escalas ni rebotes.
- **Permisos:** ninguno. **Esfuerzo:** medio.
- **Objeción y solución:** los críticos no ponen objeciones de fondo. `PredictiveBackHandler` está en activity-compose 1.13.0, y con `targetSdk` 37 el atrás predictivo ya está activo, así que no hace falta el atributo del manifiesto. Hay que cambiar los `BackHandler` de `MainActivity.kt:157` y `SearchScreen.kt:182`. Si One UI 9 no envía eventos de progreso, el cierre se queda como hoy y no se pierde nada.

### 2.3 Fijar desde cualquier app (A5)
- **Qué es:** «Añadir a inicio» de Chrome, Maps o WhatsApp llega a Alisio. Una hoja con su estilo dice «Fijar ‹Ir a casa› de Maps». El destino por defecto es la barra (el acceso sale al buscar), con una pila como segunda opción.
- **Por qué la distingue:** hoy ninguna actividad tiene el intent-filter, así que «Añadir a inicio» falla sin avisar, y eso parece un defecto. Los launchers minimalistas lo ignoran. Alisio lo mete en sus propias piezas, sin rejilla.
- **Permisos:** solo el rol HOME, que ya tiene. **Esfuerzo:** medio. Sube a medio-alto si incluye el dock.
- **Objeción y solución:** `DockSlot` y `StackItem` solo guardan `app_key`. La primera versión deja el dock fuera para no tocar el esquema. Si después se añade, lleva columnas de acceso (paquete, id y usuario, como `IconGesture`) y su `Migration`. No se declara `ACTION_CONFIRM_PIN_APPWIDGET`, porque el sistema diría a las apps que Alisio acepta widgets. Chrome instala muchas webs como WebAPK, que ya llegan como apps.

### 2.4 La pausa que cuenta (A12)
- **Qué es:** bajo el título de la pausa, en `bodyMedium` y `TextSecondary`, va un dato de hoy: «3.ª vez hoy · 38 min». No hay gráficos ni rachas.
- **Por qué la distingue:** Escape, minimalist phone y Olauncher solo ponen una cuenta atrás. El estudio de one sec indica que la fricción con un dato que hace pensar reduce los intentos. Nadie pone el uso real de hoy en el momento de decidir.
- **Permisos:** ninguno nuevo (`PACKAGE_USAGE_STATS` ya concedido). **Esfuerzo:** pequeño.
- **Objeción y solución:** la espera que sube por tramos y el evento «No abrir» quedan fuera, porque añaden castigo y estado y pueden hacer que el dueño apague la pausa. El recuento de `launch_event` solo ve las aperturas desde Alisio. El texto lo dice, o se usa solo el uso del sistema, para no dar una cifra que engañe. Se elige con la vista previa «Ver la pausa».

### 2.5 La próxima alarma en la línea de la fecha (A13)
- **Qué es:** si hay una alarma en las próximas 12 horas, la línea de la fecha dice «Alarma 7:30», con el estilo de la fecha. Un toque abre su pantalla con `getShowIntent`.
- **Por qué la distingue:** sigue la tecnología tranquila (Weiser y Brown, Amber Case). Pixel impone At a Glance con mucho contenido. Alisio da un solo dato, y solo cuando importa, que es antes de dormir.
- **Permisos:** ninguno. **Esfuerzo:** pequeño.
- **Objeción y solución:** `getNextAlarmClock` devuelve alarmas de cualquier app (calendarios, Tasker), así que se mira `getCreatorPackage` del `showIntent` y solo se acepta el reloj. Se quita la opción «última app de audio», porque repite el reproductor. Basta un interruptor con vista previa en Ajustes → Inicio. El receptor de `ACTION_NEXT_ALARM_CLOCK_CHANGED` se registra en `onStart` y se retira en `onStop`. Antes hay que comprobar que el Reloj de Samsung publica su alarma por `AlarmClockInfo`.

### 2.6 Olvidar en el sitio donde se ve (A2, reducida)
- **Qué es:** no hay una página nueva. «Olvidar» aparece donde está la decisión aprendida: en el «Sueles usar…» de la persona elegida y en el motivo de la placa de Ahora (mantener, o una acción de TalkBack). Funciona con `UndoBar`.
- **Por qué la distingue:** Pixel y las sugerencias contextuales de Android aprenden en el dispositivo, pero solo dejan apagarlo todo o borrarlo todo. Alisio hace que cada decisión automática tenga un porqué que se puede corregir en el sitio. Así se cumple «siempre con su motivo».
- **Permisos:** ninguno. **Esfuerzo:** medio.
- **Objeción y solución:** la página de «lo aprendido» se visita dos veces y se olvida, y devolver las sugerencias silenciadas ya existe en Ajustes → Inicio. Olvidar no borra filas de `launch_event`, porque eso cambiaría la frecencia de la búsqueda y la regla Bluetooth, y la costumbre se volvería a aprender en unos días. Se guarda una marca de «olvidado» (regla y clave), en `suggestion_feedback` o en una tabla pequeña con su `Migration`, y las funciones puras de `core/` la respetan.

### 2.7 Ahora en vivo (A1), solo si pasa la prueba en el S26
- **Qué es:** si hay una Live Update en curso (viaje, temporizador, pedido), la placa de Ahora enseña su título, el texto corto o la cuenta atrás, con el motivo «En curso · Maps». Un toque abre su `contentIntent`. Al terminar, vuelve la sugerencia normal.
- **Por qué la distingue:** One UI la pone en la Now Bar de la pantalla de bloqueo, no en el Inicio, y ningún launcher hace de ella la pieza central. Es una sola cosa en curso con su motivo, no un resumen de notificaciones (§3).
- **Permisos:** ninguno nuevo: el listener que ya existe. **Esfuerzo:** medio.
- **Objeción y solución:** primero hay que ver en el S26 qué publican de verdad Maps, Reloj y Uber. Si casi nada lo publica, se descarta. En One UI muchas apps publican avisos en curso sin `FLAG_PROMOTED_ONGOING`, así que se acepta también `FLAG_ONGOING_EVENT` con `EXTRA_PROGRESS` o con cronómetro, dentro de una lista de categorías (`CATEGORY_NAVIGATION`, `PROGRESS`, `TRANSPORT`, `STOPWATCH`). La primera versión no lleva puntos ni segmentos de `ProgressStyle` ni el deslizar para apartar. Tiene que dar más que el chip de la barra de estado. Lanzar el `contentIntent` puede chocar con el mismo límite de inicio de actividad que §11 encontró en el reproductor: hay que probarlo antes de prometerlo. La cuenta atrás se refresca con el tic de minuto, sin bucle.

### 2.8 El Inicio respeta al sistema (A11, en dos entregas)
- **Qué es, primera entrega:** las apps suspendidas por Bienestar digital se ven a α 0,38 y su Interior dice «En pausa por Bienestar digital». Ahora y Recientes no sugieren las apps que `shouldHideFromSuggestions` marca.
- **Segunda entrega:** las apps archivadas siguen en Zonas y en la barra con el secundario «Archivada · tocar para restaurar».
- **Por qué la distingue:** es pulido invisible. Los launchers de terceros ignoran estos estados o esconden las apps sin explicar. Alisio enseña el estado con su motivo, sin ajustes nuevos.
- **Permisos:** ninguno. Play hace la descarga de una app archivada. **Esfuerzo:** medio en total.
- **Objeción y solución:** son cinco comportamientos repartidos en varias piezas, así que se divide. Ocultar los puntos con No molestar queda fuera: ese modo es para no recibir avisos, no para no verlos. Samsung suspende con su propio diálogo, y las apps distractoras solo vienen marcadas con un Modo de concentración activo. La segunda entrega solo vale si One UI 9 archiva apps de forma automática. Hay que comprobarlo antes.

### En reserva (sobreviven, pero valen menos)
- **Alisio en un archivo (A16):** exportar e importar la configuración a JSON por SAF. El riesgo es real: el manifiesto tiene `allowBackup="false"` y reinstalar pierde el dock, las pilas, las islas y los gestos. Sin la vista previa de restauración, solo con un aviso de las apps que faltan y un número de versión en el JSON. Los accesos directos hay que volver a fijarlos con `LauncherApps.pinShortcuts`, y `shortcut_user` cambia entre dispositivos. **Decide el dueño:** o esto, o activar las reglas de copia del sistema, que es más barato y tampoco pide INTERNET.
- **Canal delante del nombre (A4):** «wa laura», «tel laura». Es un parser puro y barato, pero «laura» más Enter ya abre el canal principal. Solo con pocos verbos fijos en los dos idiomas (tel/llamar, wa, vid, sms). Un verbo solo cuenta si va seguido de un espacio, para no tapar a Telegram. Prioridad baja.
- **Salida de audio desde el reproductor (A15):** mantener la placa abre el selector del sistema. `showSystemOutputSwitcher()` sin argumentos abre el selector de Alisio, que no reproduce nada, y la sobrecarga con `MediaSession.Token` (API 36.1) puede no aceptar el token de otra app. Se hace una prueba en el S26. Si falla, se descarta. Fuera el acento sacado de la carátula, porque mete colores fuera de la paleta.

## 3. Pulido de lo que ya hay

1. **Primer toque y teclado fiables (P1).** Es la base de «muy pulido» y la queja más repetida de Niagara.
   - Prueba manual fija en release: volver al Inicio y, en menos de 300 ms, deslizar arriba, usar Trazo y tocar el dock. Se repite con el acceso a notificaciones activado y desactivado. Los tiempos medidos se apuntan en §11.
   - No recomponer el Inicio en `onResume` ni en `onNewIntent`.
   - Si el IME no aparece en unos 150 ms, se vuelve a pedir el foco y el teclado una sola vez.
   - Si One UI quita el rol, se ofrece «Volver a ser el inicio» con `RoleManager.createRequestRoleIntent`.
   - No se añade un test de UI: el proyecto no tiene `androidTest` y esa ventana de tiempo no se puede comprobar con Compose. Para medir se usa logcat en release o Macrobenchmark.
2. **Ahora y el historial más fiables (P4).**
   - «Versión nueva» vale solo para las apps de la isla Dev. Esto cierra el pendiente de §11 de que Play tapa la costumbre.
   - El límite de 90 días se recalcula en cada `refreshNow` y el historial se poda una vez al día.
   - `NowRule` se guarda por un id estable. Hace falta una migración que traduzca los nombres del enum que ya hay en `suggestion_feedback.rule`.
   - Algo abierto fuera de Alisio se da por visto con `ACTIVITY_RESUMED` (API 35), sin guardarlo en disco.
   - Hecho: el gasto rápido está borrado entero (`Expense.kt` y su test, `Prefs.expenseTemplate`, la fila de la barra, su bloque en Ajustes › Búsqueda y sus textos). La clave `expense_template` puede seguir en el DataStore de quien la guardó; nadie la lee.
   - Los descartes por franja horaria se hacen solo si al usarlo un descarte mata de verdad una costumbre. Piden recrear la tabla en la migración.
3. **Cifras tabulares en el reloj (A9, reducida).** Lo que siempre se ve es que la hora no baile, no la animación.
   - Primero hay que comprobar `tnum` en la tabla GSUB de `space_grotesk.ttf` y de las cuatro familias de `FontChoice` (DISENO §2.1). Si una fuente no lo trae, cada cifra lleva un ancho fijo.
   - La animación es opcional. Si se hace, es solo un fundido corto en las cifras que cambian, sin la subida de 12 dp, y respeta `ANIMATOR_DURATION_SCALE=0`.
   - Partir el `Text` con `TextAutoSize` (`HomeScreen.kt:623`) obliga a calcular antes un tamaño único con `TextMeasurer`.
4. **Reloj alineado al filo (P2).** La primera cifra toca el filo de 24 dp por su tinta.
   - Se calcula una corrección fija por familia y por tamaño con una cifra de referencia (el 0), medida con `Paint.getTextBounds`. `getBoundingBox` no sirve, porque da la caja de avance y no la tinta.
   - No se mide la cifra de cada minuto: el reloj se movería a los lados al pasar de 09:59 a 10:00.
   - El desplazamiento se aplica en `onTextLayout` con `Modifier.offset`. Se prueba con `TextAutoSize` y con letra a 1,3.

## 4. Descartadas y por qué

- **Apodos en la barra (A3):** la frecencia con tolerancia a erratas ya lleva «b» al banco. Pide una tabla, filas nuevas en tres sitios y que el dueño gestione un vocabulario. Si un resultado no sale primero, se arregla el ranking.
- **Instalando, en Ahora (A6):** hay pocas instalaciones y el dueño suele mirarlas en Play. Las actualizaciones automáticas abrirían sesiones y empeorarían el problema que §11 ya tiene.
- **Ahora también para personas (A7):** las costumbres con personas son semanales o irregulares, y la regla de ±30 min en 3 de 5 días no las detecta. Un acierto falso con «Llamar a Mamá» molesta mucho más que con una app.
- **Trazo con segundo eje (A8):** subir dentro de la rejilla ya significa «la app de arriba». Se confunde, nadie lo encuentra sin explicación y aumenta los errores en un gesto que se hace sin mirar.
- **Dial sobre la cifra (A17):** escribir «temporizador 10» es más rápido y exacto. El dial choca con el desplazamiento y con el teclado abierto.
- **Iconos sin capa adaptativa con tono dominante (P3):** `AppIcons.drawUniform` ya aplica `LEGACY_INSET` sobre una placa y recorta a la forma común. El tono dominante solo añadiría colores fuera de la paleta.

## 5. Fuentes

**Plataforma**
- Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update
- Novedades de Android 16: https://developer.android.com/about/versions/16/features
- Novedades de Android 17: https://developer.android.com/about/versions/17/features
- LauncherApps: https://developer.android.com/reference/android/content/pm/LauncherApps
- NotificationListenerService: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- HapticFeedbackConstants: https://developer.android.com/reference/android/view/HapticFeedbackConstants
- Atrás predictivo en Compose: https://developer.android.com/develop/ui/compose/system/predictive-back
- MediaRouter2: https://developer.android.com/reference/android/media/MediaRouter2
- UsageEventsQuery: https://developer.android.com/reference/android/app/usage/UsageEventsQuery
- Archivos con SAF: https://developer.android.com/training/data-storage/shared/documents-files
- Novedades de Android 15 (apps archivadas): https://developer.android.com/about/versions/15/features

**One UI y competencia**
- Live Updates en One UI 8: https://www.androidauthority.com/one-ui-8-live-updates-support-3573794
- Now Bar con apps de terceros: https://9to5google.com/2025/07/09/samsung-one-ui-8-now-bar-third-party-apps/
- Sugerencias de Pixel Launcher: https://www.androidauthority.com/pixel-launcher-app-suggestions-3719732/
- Sugerencias contextuales de Android: https://9to5google.com/2026/05/13/android-contextual-suggestions/
- Teclado de Niagara: https://niagaralauncher.featurebase.app/p/search-keyboard-not-opening
- Retraso de Niagara al volver: https://niagaralauncher.featurebase.app/p/gesture-navigation-home-screen-redraw-delay-1-sec-lag-returning
- Gestos en Niagara: https://help.niagaralauncher.app/article/7-gesture-navigation
- Gestos en Lawnchair: https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues
- Cierre de Nova: https://www.androidauthority.com/nova-launcher-shutdown-3595611/
- Lawnchair 15 beta 1: https://lawnchair.app/blog/lawnchair-15-beta-1/
- Launchers minimalistas en 2026: https://www.androidauthority.com/best-minimalist-android-launchers-2026-3709772/

**Pausa y diseño**
- Estudio de one sec: https://pmc.ncbi.nlm.nih.gov/articles/PMC9974409
- Haliburton et al., CHI 2024 (los estudios de one sec los firma su autor): https://www.medien.ifi.lmu.de/pubdb/publications/pub/haliburton2024chi/haliburton2024chi.pdf
- Tecnología tranquila: https://en.wikipedia.org/wiki/Calm_technology
- Principios de Amber Case: https://www.caseorganic.com/post/principles-of-calm-technology
- Tipografía práctica: https://practicaltypography.com/

**Repositorio**
- `docs/PLAN.md` §3, §9 y §11 (fases 3 a 6), y `docs/DISENO.md` §2.1, §2.7 y §6.9, en `C:\Users\jessu\dev\alisio`.

**Pendiente de comprobar en el S26 antes de implementar:** campos de Live Update de Maps, Reloj y Uber; la alarma de Samsung en `AlarmClockInfo`; si las vibraciones semánticas suenan distintas; si llegan los eventos de progreso del atrás; el archivado automático en One UI 9; y `tnum` en las fuentes.
