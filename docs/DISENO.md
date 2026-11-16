# Alisio · Guía de diseño (fase 6)

Guía vigente desde la fase 6. Donde contradiga la §6 de `docs/PLAN.md`, manda esta guía.
Los valores marcados «comprobar» se calcularon sin verlos en pantalla: hay que mirarlos en el emulador con un fondo blanco, una foto cargada y escala de fuente 1,3 antes de darlos por buenos. Si se cambia un valor, se cambia aquí y en el token, en ningún otro sitio.

## 1. Idea: «Filo y placa»

- **Un filo.** Todo empieza a 24 dp del borde: reloj, fecha, primer icono del dock, iconos de fila, etiquetas y títulos. El texto de toda fila empieza en un segundo filo, a 84 dp.
- **Una placa.** Lo que flota sobre el fondo de pantalla usa un solo material: la placa, oscura, translúcida, sin borde y sin sombra. Sobre fondo opaco (barra, ajustes, hojas) no hay cajas: solo filas, espacio y letra.
- **El acento responde.** El acento solo marca tres cosas: lo que hará Enter, lo elegido y la acción principal. Nunca decora.
- **Todo crece desde el pulgar.** Campo abajo; encima, las salidas; encima, el mejor resultado; lo secundario, arriba.
- **Se elige mirando.** Cada opción visual se elige tocando una vista previa dibujada con las piezas reales.

Dos voces: Space Grotesk para lo que se mira (hora, títulos, cifras) e Inter para lo que se lee.

## 2. Tokens

Todos viven en `ui/theme` (`AlisioTokens`, `AlisioTypography`, `AlisioMotion`). Ninguna pantalla define constantes propias de color, alfa, margen, radio ni tamaño de letra.

### 2.1 Tipografía

| Papel | Ranura | Familia y peso | Tamaño / interlineado | Notas |
| --- | --- | --- | --- | --- |
| Reloj | `AlisioTokens.Clock` | Space Grotesk Light | 112 / 112 sp | −0,045 em; sin cambio |
| Cifra | `AlisioTokens.Numeral` | Space Grotesk Light | 40 / 44 sp | −0,02 em. Resultado del cálculo y vista previa del velo. La cuenta atrás de la pausa conserva su tamaño actual |
| Título de pantalla | `headlineLarge` | Space Grotesk Medium | 32 / 38 sp | Ajustes, Zonas, pausa, bienvenida |
| Título de hoja | `headlineSmall` | Space Grotesk Medium | 24 / 30 sp | Todas las hojas y la cabecera de persona. Sustituye cada `.copy(fontSize = 24.sp)` |
| Título de fila | `titleMedium` | Inter Medium | 16 / 22 sp | Hoy SemiBold. Una línea |
| Texto y campo | `bodyLarge` | Inter Regular | 15 / 22 sp | La fecha del Inicio usa este estilo en Medium |
| Secundario | `bodyMedium` | Inter Regular | 13 / 18 sp | `TextSecondary` |
| Acción, botón y chip | `labelLarge` | Inter SemiBold | 14 / 20 sp | |
| Etiqueta de sección | `AlisioTokens.SectionLabel` | Inter SemiBold | 13 / 18 sp | Sin interletrado, `TextSecondary`, mayúscula solo inicial |
| Nombre bajo icono | `AlisioTokens.DockLabel` | Inter Medium | 11,5 / 14 sp | Dock, Zonas, Trazo y teselas |
| Letra del índice | `AlisioTokens.RailLetter` | Inter SemiBold | 11 / 14 sp | Índice de Trazo y de Resto |

- Las cifras usan `fontFeatureSettings = "tnum"` solo si `space_grotesk.ttf` lo trae (comprobar); si no, se omite.
- Las mayúsculas nunca se escriben en `strings.xml` ni se aplican con `uppercase()`.
- SemiBold queda para acciones, etiquetas de sección e índice. La jerarquía sale de tamaño, familia y color.

### 2.2 Espaciado (rejilla de 4 dp)

| Token | Valor | Uso |
| --- | --- | --- |
| `Edge` | 24 dp | El filo. Margen de contenido en todas las pantallas |
| `Outset` | 12 dp | Margen exterior de placas, filas, campos y teselas. Con 12 dp de relleno interior, su contenido cae en el filo |
| `IconSlot` | 44 dp | Hueco del icono de fila |
| `IconGap` | 16 dp | Del hueco al texto. Segundo filo: 24 + 44 + 16 = 84 dp |
| `RowMinHeight` | 56 dp | 68 dp con secundario. Entre filas: 0 |
| `GroupGap` | 28 dp | Aire antes de una etiqueta de sección |
| `LabelGap` | 8 dp | Aire después de la etiqueta |
| `TitleGap` | 24 dp | Bajo un título de pantalla; 20 dp bajo un título de hoja |
| `PlateGap` | 12 dp | Entre piezas del pie del Inicio y entre islas |

Se borran los `ScreenMargin` locales (Inicio, barra, ajustes, pausa, aviso, Zonas).
Toda altura de una pieza con texto es `heightIn(min = …)`, nunca `height(…)`.
Toque mínimo: 48 dp.

### 2.3 Radios

| Token | Valor | Uso |
| --- | --- | --- |
| `PillShape` | 28 dp | Campos, botones, chips, esquinas superiores de hojas y del cuerpo de ajustes |
| `CardShape` | 22 dp | Placas (Ahora, islas) y teselas de vista previa |
| `RowShape` | 20 dp | Fila pulsada, relleno del destino de Enter |
| `DockIconShape` | 17 dp | Iconos de app, carpeta de pila, hueco vacío y carátula del reproductor |

Avatares en círculo. `Shapes.small` pasa a `RowShape`. No hay un quinto radio.

### 2.4 Color y superficies

Colores base, sin cambio: `Background` `#0B0C0E`, `Surface` `#15171A`, `Text` `#F2F3F5`, `TextSecondary` `#9AA0A8`, `TextTertiary` `#8A9099` (solo texto de marcador de campo). Acentos: `#6FD6C6` Turquesa, `#8FB4FF` Azul, `#F2B872` Ámbar, `#C9A7FF` Lila.

| Token | Valor | Uso |
| --- | --- | --- |
| `Veil` | Degradado vertical de `Background`; ver §3 | Inicio y vista previa de ajustes |
| `ZonesVeil` | `Background` α 0,70 uniforme | Zonas |
| `Scrim` | `Background` α 0,86 | Trazo abierto |
| `Plate` | `Surface` α 0,86 | Todo lo que flota sobre el fondo: píldora, Ahora, reproductor, islas, carpeta de pila, hueco vacío. La píldora, Ahora, En curso y el reproductor siguen la opacidad elegida (`homePlate`: 0,86, 0,62 o 0,40; con 0,62 y 0,40 su secundario pasa a `Text` α 0,8) |
| `Background` opaco | `#0B0C0E` | Barra, ajustes, pausa, bienvenida, aviso |
| `Surface` opaco | `#15171A` | Hojas inferiores y barra de deshacer |
| `Fill` | `Text` α 0,06 | Único relleno dentro de fondo opaco, hoja o placa: campo, chip sin elegir, botón tonal, avatar sin foto, fondo de tesela, asa de hoja, pista del interruptor apagado |
| `AccentSoft` | `Accent` α 0,14 | Relleno del destino de Enter y del chip elegido |
| `OnWallpaperShadow` | Negro α 0,45, desenfoque 10 dp, sin desplazamiento | Texto que va directo sobre el fondo |

- Se borran `SurfaceElevated`, `CardBorder` y `ControlBorder`. En el `colorScheme`, `surfaceContainer`, `outline` y `outlineVariant` apuntan a `Fill` (resuelto sobre `Surface`).
- Texto directo sobre el fondo: siempre `Text` opaco con `OnWallpaperShadow`. Se quitan las alfas 0,82, 0,9 y 0,72.
- Texto sobre acento: `Background`.
- No hay rojo. Lo destructivo va en `TextSecondary` y se puede deshacer.

### 2.5 Acento: usos permitidos

1. **Destino de Enter:** relleno `AccentSoft` sin borde e `ic_keyboard_return` de acento al final.
2. **Elegido:** anillo de la tesela, `ic_check` en listas, interruptor encendido, chip elegido, muestra de acento marcada.
3. **Acción principal:** relleno de `PrimaryButton` y texto de `TextAction`.
4. **Señal viva:** cursor, letras coincidentes de la búsqueda, punto de no leído, marca de deslizar sobre un icono.

Fuera de esos cuatro usos no hay acento: ni en iconos de fila, ni en etiquetas, ni en bordes de campo, ni en textos informativos, ni en brillos.

### 2.6 Estados

| Estado | Dibujo |
| --- | --- |
| Pulsado | `Text` α 0,08 con la forma del elemento (indicación de Material, definida una vez en `AlisioTheme`). Iconos de app sobre el fondo: escala 0,94 |
| Destino de Enter | `ItemRow(enterTarget = true)`: relleno `AccentSoft`, `RowShape`, `ic_keyboard_return` de 20 dp |
| Elegido en lista | `ic_check` de acento al final, sin caja. `selectable(role = RadioButton)` |
| Tesela elegida | `Modifier.selectedTile(true)`: anillo de acento de 2 dp, sin cambiar el relleno. Nombre en `Text`; las demás, en `TextSecondary` |
| Chip elegido | Relleno `AccentSoft`, texto de acento. Sin elegir: `Fill`, texto `Text`. Nunca borde |
| Encendido / apagado | `Switch` de Material 3. Encendido: pista `Accent`, pulgar `Background`. Apagado: pista `Fill`, pulgar `TextSecondary`, sin borde. Fila con `toggleable(role = Switch)` |
| Sin requisito | Contenido a α 0,38, `disabled()`, sin estado. El requisito aparece como acción «Permitir» |
| Arrastrando | Escala 1,04. Los destinos posibles a α 1; los imposibles, a α 0,38 |
| Destino de arrastre | `selectedTile(true)` sobre la isla bajo el dedo |
| Destructivo | `TextSecondary`, último de su grupo, separado `GroupGap`, con `UndoBar` |

### 2.7 Movimiento (`ui/theme/Motion.kt`, objeto `AlisioMotion`)

| Token | Valor | Uso |
| --- | --- | --- |
| `Fast` | 120 ms, `LinearOutSlowInEasing` | Pulsado, marcas, anillo |
| `Standard` | 220 ms, `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | Subpáginas de ajustes, ocultar el Inicio, aparecer Ahora y reproductor, cambio de contenido de una hoja |
| `Screen` | 320 ms, la misma curva | Barra, Zonas, ajustes |
| `Exit` | 160 ms, `CubicBezierEasing(0.3f, 0f, 1f, 1f)` | Toda salida |
| `Follow` | `spring(dampingRatio = 0.9f, stiffness = 500f)` | Lo que sigue al dedo |

- Solo se animan la alfa y un desplazamiento de 12 dp como máximo: lo que entra sube 12 dp y aparece; lo que sale se desvanece. Única excepción: las letras de Trazo, que se abren desde el dedo y llevan la lupa (crecen y se apartan del pulgar) porque siguen al dedo, no son una transición.
- Sin rebotes, sin escalonados, sin nada en bucle.
- Las hojas usan la animación de `ModalBottomSheet` para abrir y cerrar. El gesto atrás no: el de Material escala la hoja, y `AlisioSheet` lo sustituye por el de abajo.
- La vibración se pide por su significado con `AlisioHaptics` (`ui/theme`), nunca con una constante suelta: `step` (paso: cambiar de letra o de app en Trazo, pasar sobre una isla que acepta, cambiar de puesto al reordenar), `done` (hecho: el gesto acaba en una acción, Enter, poner en el dock, soltar en una isla), `limit` (límite: lo pedido no se puede hacer), `threshold` (cruzar o deshacer el umbral de un deslizar), `dragStart` (algo se despega para arrastrarlo) y `toggle` (un interruptor, distinto al encender y al apagar).
- Atrás sigue al dedo (`FollowingBackHandler`, `ui/components/BackFollow.kt`): la capa que se va a cerrar baja hasta 12 dp y pierde alfa con el progreso (0,4 una pantalla; 0,1 una hoja, que es opaca); soltar antes la devuelve con `Follow` y acabar la cierra con `Exit`. Toda pantalla que tapa el Inicio va en un `BackFollowLayer`, y toda hoja es un `AlisioSheet`, que ya lo trae.
- La hora grande va con `ClockText`: cifras tabulares (`tnum` en `AlisioTokens.Clock`) y la tinta de la primera cifra en el filo.
- Con las animaciones del sistema desactivadas, las duraciones son 0.

## 3. Legibilidad sobre cualquier fondo

1. **Velo único.** `WallpaperVeil(dim, lightWallpaper)` en `ui/components`, sacado de `HomeScreen.kt`. Lo usan el Inicio y la vista previa de ajustes. Las paradas las calcula una función pura `veilStops(dim, lightWallpaper)` en `core/`, con tests.
2. **Paradas.** Arriba `max(0,38 × dim, suelo)`; al 45 % de la altura `max(0,30 × dim, suelo × 0,6)`; abajo `(valor actual).coerceIn(0,66, 1)`.
3. **Suelo según el fondo.** Se lee `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` y se escucha `addOnColorsChangedListener`. Con `HINT_SUPPORTS_DARK_TEXT`, `suelo = 0,50`; sin esa pista o con `null`, `suelo = 0`. «Poco», «Medio» y «Mucho» siguen graduando por encima del suelo.
4. **Sombra única.** `OnWallpaperShadow` con `style.copy(shadow = …)` en reloj, fecha, `DockLabel` del dock, título y pista de Zonas y letras del índice. No se aplica dentro de placas ni sobre fondo opaco.
5. **Placas.** `Plate` a 0,86 deja el secundario en torno a 4,6:1 sobre blanco puro (comprobar). No se baja para lucir el fondo: el fondo se ve en los dos tercios libres del Inicio. Excepción elegida por el dueño: las placas del pie pueden ser medias o translúcidas (Ajustes → Aspecto), y entonces su texto secundario se aclara para no bajar de 4,5:1.
6. **Sin texto fantasma.** Con Zonas, Trazo o los ajustes abiertos, el contenido del Inicio (reloj, fecha, Ahora, reproductor, dock, píldora, índice) pasa a alfa 0 con `Standard`. El `drawBehind` del velo no se toca.
7. **Sin brillo radial** en Inicio ni en Zonas.
8. **Texto siempre claro.** No hay modo de texto oscuro: la pista describe el fondo entero, no la franja.

Si con fondo blanco y «Poco» la fecha no se lee, se sube `suelo` a 0,57; no se añade otra capa.

## 4. Reglas de composición

### 4.1 Cuándo hay contenedor

Solo existen estos contenedores:

1. **Placa:** un grupo que flota sobre el fondo de pantalla.
2. **Hoja:** una capa temporal (hoja inferior, barra de deshacer).
3. **Control con forma propia:** campo, chip, botón, interruptor.
4. **Tesela de vista previa:** la imagen de un resultado que se puede elegir.
5. **Relleno del destino de Enter.**

Normas:

- Ningún contenedor dentro de otro, salvo un control dentro de una placa o de una hoja.
- Ningún elemento lleva a la vez fondo y borde. Los únicos trazos de la app son el anillo de la tesela elegida y el aro del punto de no leído.
- Un icono de app nunca va dentro de otra caja. Un vector de fila va suelto: 24 dp, `TextSecondary`, centrado en `IconSlot`.
- Sobre fondo opaco no hay tarjetas ni divisores.

### 4.2 Grupos

- Se separan con `GroupGap` y una `SectionLabel` con `semantics { heading() }`.
- La etiqueta solo existe si el grupo puede tener más de una fila. Un resultado único (cálculo, reloj, gasto, portapapeles) no lleva etiqueta.
- Como máximo tres etiquetas en una hoja.

### 4.3 Fila (`ItemRow`, la única)

- Ancho completo menos `Outset` por lado; relleno interior de 12 dp; alto mínimo `RowMinHeight`.
- **Inicio de la fila**, uno de: icono de app de 44 dp (`icon`), `Avatar` circular de 44 dp (`avatarName`, con foto si la hay), o vector (`leadingIcon`). Se borra `leading: String`.
- **Centro:** título en una línea; secundario opcional con `subtitleLines` (1 en la barra, 2 en ajustes y hojas).
- **Final**, exactamente uno o ninguno: `checked: Boolean?` (interruptor), `selected: Boolean?` (`ic_check`), `enterTarget` (`ic_keyboard_return`), una acción de texto (`trailing`, solo verbos: «Permitir», «Cambiar», «Elegir»), o hasta dos `RoundIconButton`.
- `onLongClick` opcional con `combinedClickable` y `onLongClickLabel`.
- Los secundarios solo dicen un estado o un dato nuevo (dispositivo conectado, permiso que falta, aviso). Se borran los que repiten el título o lo que una vista previa ya enseña.

### 4.4 Botones y chips (`ui/components/Buttons.kt`)

- `PrimaryButton`: 56 dp, `PillShape`, relleno de acento, texto `Background`. Uno por pantalla como máximo. Admite icono inicial.
- `TonalButton`: 48 dp, `PillShape`, relleno `Fill`, icono y texto en `Text`.
- `TextAction`: 48 dp de toque, texto de acento; variante `secondary` en `TextSecondary` para lo destructivo.
- `RoundIconButton`: 48 dp, con `iconSize` (24 dp por defecto) y `container` opcional (`Fill`). Sustituye a `PlayerButton`, al botón de borrar del campo y al de abrir contacto.
- `Chip(icon?, selected: Boolean?)`: 40 dp visibles en 48 dp de toque. `selected = null` es una salida (acción); con valor es elección y lleva `selectable(role = RadioButton)`. Las acciones de una hoja nunca son chips: son filas.
- Varios chips van en `FlowRow`, nunca en una fila con desplazamiento que corte el último.

### 4.5 Selector con vista previa (`PreviewChoice`)

Sale de `StackIconOption`, que desaparece.

- Teselas con fondo `Fill`, `CardShape`, en fila de pesos iguales (2 o 3 opciones) o en fila horizontal con teselas de 104 dp (más de 3).
- Dentro, un área de al menos 72 dp de alto con el resultado dibujado con los componentes reales a escala.
- Debajo, el nombre en `bodyMedium`. Sin descripciones.
- La elegida lleva `selectedTile`. Rol `RadioButton`.
- Una opción con resultado visible nunca se elige con interruptor. El interruptor queda para lo que no tiene resultado que enseñar (gestos) y para lo que ya se ve en la vista previa del Inicio que hay encima.

### 4.6 Hoja inferior

- `ModalBottomSheet` con `containerColor = Surface`, esquinas superiores `PillShape`, asa de 32 × 4 dp en `Fill`.
- Título en `headlineSmall` en el filo; `TitleGap` de 20 dp; contenido en filas.
- Nunca una hoja sobre otra: un segundo paso cambia el contenido de la misma hoja y añade `RoundIconButton(ic_arrow_back)` junto al título.
- Lo destructivo va lo último, con deshacer.

### 4.7 Selector de apps (`AppPicker`, único)

- Lista con `reverseLayout` y `PillTextField` abajo, pegado al teclado, como la barra.
- Modo simple (un toque elige y cierra) o múltiple (cada toque marca o desmarca; no cierra).
- Marca única: `ic_check` de acento. Un grupo fijo opcional arriba de la lista: va dentro de ella, pegado encima de la primera fila y separado por la etiqueta «Apps», y se desplaza con ella.
- Lo usan: hueco del dock, pila, destino al deslizar, asistente, búsqueda web, apps con pausa e islas.

### 4.8 Deshacer (`UndoBar`, único)

- `SnackbarHost` de Material 3: `Surface` opaco, `PillShape`, texto `bodyLarge` y `TextAction` «Deshacer». Anclada sobre el dock o sobre el pie de la hoja.
- Se usa en: quitar pila, dejar vacío un hueco, descartar Ahora, quitar una app de la pausa, borrar isla y sustituir una app del dock.
- No hay confirmaciones de dos toques.

### 4.9 Iconos

- Vectores de Material Symbols en `res/drawable`, 24 dp. Con `contentDescription` si no llevan texto al lado.
- Hay que añadir: `ic_close`, `ic_search`, `ic_settings`, `ic_check`, `ic_drag_indicator`, `ic_keyboard_return`, `ic_calculate`, `ic_contacts`, `ic_auto_awesome`, `ic_stacks`, `ic_swap_horiz`, `ic_palette`, `ic_wallpaper`, `ic_open_in_new`, `ic_help`, `ic_visibility_off`, `ic_home`. Antes de crear uno, se busca el existente: ya están, entre otros, `ic_add`, `ic_remove`, `ic_edit`, `ic_arrow_back`, `ic_info`, `ic_person`, `ic_link`, `ic_receipt`, `ic_schedule`, `ic_pinch`, `ic_title`, `ic_zones`, `ic_play`, `ic_pause`, `ic_notifications`, `ic_lock`, `ic_bluetooth`, `ic_swipe_up` e `ic_swipe_down`.
- Ningún carácter hace de icono. La única excepción es el símbolo que el dueño escribe para una pila: es contenido suyo.

## 5. Prohibido

- Tarjetas dentro de tarjetas. Fondo más borde, o borde más sombra, para decir lo mismo.
- Bordes de contorno, sombras de elevación, divisores.
- Caracteres como icono: «‹», «›», «✕», «+», «≡», «⋮», «•», flechas Unicode, emojis.
- Etiquetas en mayúsculas, versalitas o con interletrado.
- Acento como decoración. Degradados de color, brillos, desenfoque o vidrio. Excepciones (fase 7): la pista de `ColorSlider`, donde el degradado es lo que se elige, y la franja de 24 h del color del día, que es la vista previa de lo elegido. El anillo de progreso de la app que suena y el arco del temporizador en el dock son acento como señal viva (§2.5, uso 4).
- Color tomado del fondo de pantalla (Material You) sin que se elija. Desde la fase 7 el editor del acento propone los colores del fondo, ya legibles; solo se usan si el dueño toca uno. Fondos animados. Iconos 3D.
- Filas «Sí/No» y cualquier estado escrito como texto al final de una fila.
- Textos que explican lo que ya se ve.
- Chips con desplazamiento horizontal para acciones.
- Dos formas de pintar lo mismo: antes de crear un componente se extiende el que hay.
- Alfas, márgenes, radios o tamaños de letra escritos en una pantalla.
- Texto de interfaz en Kotlin; un texto en un solo `strings.xml`; textos sin uso en cualquiera de los dos.
- Animaciones con rebote, escalonadas o en bucle.
- Una tercera familia tipográfica.

## 6. Pantallas

En todas: no se pierde ninguna función ni ningún gesto de los que hay hoy.

### 6.1 Inicio

Arriba:

- Reloj en el filo, 40 dp bajo la barra de estado, `Clock`, con sombra. `maxLines = 1`; si no cabe (12 h o fuente grande), se reduce el tamaño.
- Fecha 4 dp debajo, `bodyLarge` Medium, `Text` opaco con sombra.
- Los dos son botones (`role = Button`) con `onClickLabel` «Abrir el reloj» y «Abrir el calendario».
- **Línea bajo la hora** (fase 7): con alguna pieza que decir, la fecha abre una línea de texto con su misma letra y las piezas detrás (`GlanceLine`, `feature/home/Glance.kt`): vector de `GlanceIconSize` (18 dp) en `Text`, 6 dp y el texto; 16 dp entre piezas; renglones de `InfoRow` (32 dp), como la fecha y las notas, más juntos que el toque mínimo por elección del dueño; hasta tres renglones. Sin caja, sin acento. La hora se escala con `AlisioTokens.clockScale` (tamaño elegido) y sigue el formato elegido.
- **Dock** (fase 7): de 0 a 8 huecos; con 7 u 8 el icono encoge (`dockIconSize`) y, si el dueño no ha elegido, los nombres se ocultan.
- Índice de Trazo a la derecha, centrado a 24 dp del borde, `RailLetter` con sombra. Para TalkBack: nombre «Índice alfabético de apps» y acción «Abrir Zonas».

Abajo, de abajo arriba, con `PlateGap` entre piezas:

1. **Píldora:** placa `PillShape`, `heightIn(min = 56.dp)`, `Outset` a cada lado. `ic_search` de 20 dp en `TextSecondary` a 20 dp del borde de la placa, y el texto en `TextSecondary`.
2. **Dock:** seis huecos con `Arrangement.SpaceBetween` dentro del filo. Icono de 52 dp, `DockIconShape`; `DockLabel` con sombra 6 dp debajo si `dockLabels`.
   - Punto de no leído y marca de deslizar: como hoy, en acento.
   - **Hueco vacío:** placa `DockIconShape` de 52 dp con `ic_add` de 24 dp en `TextSecondary`. Sin etiqueta. `contentDescription` «Hueco vacío del dock», `onClickLabel` «Elegir una app».
   - **Pila:** placa `DockIconShape` de 52 dp con rejilla 2 × 2 de iconos de 20 dp y 4 dp de separación, o la primera app, o el símbolo del dueño. Sin borde.
3. **Reproductor** (si suena algo y está encendido): placa `PillShape`, `heightIn(min = 64.dp)`, `Outset`. Carátula de 44 dp `DockIconShape`, título `titleMedium`, app en `bodyMedium`, y dos `RoundIconButton` (pausa o reproducir, y siguiente; `iconSize` 26 dp). Tocar la placa abre la app. Deslizarla en horizontal la oculta hasta que algo vuelva a sonar; acción de TalkBack «Ocultar reproductor». El alto real se mide y se pasa al índice de Trazo.
4. **Ahora** (si hay sugerencia): placa `CardShape`, `heightIn(min = 68.dp)`, `Outset`, relleno 12 dp. Icono de la app de 44 dp sin tesela, nombre en `titleMedium`, motivo en `bodyMedium`, `RoundIconButton(ic_close)`. Sin la etiqueta «Ahora»: va en el `contentDescription` del conjunto. Al descartar: `UndoBar` «Oculta 6 h · Deshacer»; al tercer descarte, «No volverá a sugerirse · Deshacer».

Píldora, reproductor y Ahora tienen el mismo ancho y el mismo material.

Gestos, sin cambio: deslizar arriba o tocar la píldora abre la barra; pellizcar abre Zonas; dos toques bloquea; deslizar abajo baja las notificaciones; mantener una zona vacía abre los ajustes; mantener un hueco abre su hoja o su Interior; deslizar arriba sobre un icono abre su destino.

**Trazo abierto:** contenido del Inicio oculto, `Scrim`, rejilla de apps con `DockLabel`. La celda bajo el dedo lleva `selectedTile`. La letra activa del índice va en acento.

**Pila abierta:** hoja con el nombre en `headlineSmall`, rejilla de 4 columnas (icono de 52 dp y `DockLabel`), y al final un `TextAction` «Editar». Mantener una app abre su Interior.

### 6.2 Hoja de un hueco del dock y de una pila

**Hueco vacío o con app** (`AppPicker` en modo simple):

- Título «Hueco del dock».
- Grupo fijo: fila `ic_stacks` «Crear una pila». Si todos los huecos están vacíos, fila `ic_auto_awesome` «Rellenar con las más usadas».
- Etiqueta «Apps» y la lista; la app actual lleva `ic_check`.
- Si el hueco tenía app: fila `ic_remove` «Dejar vacío» al final, en `TextSecondary`, con `UndoBar`.
- Campo de búsqueda abajo.

**Pila:**

- **Nombre:** es el título. `BasicTextField` con estilo `headlineSmall`, sin píldora, con `ic_edit` de 18 dp en `TextSecondary` a la derecha.
- **Icono:** tres `PreviewChoice` de peso igual: Carpeta, Primera app y Símbolo. Al elegir Símbolo, el carácter se escribe dentro de su tesela.
- **«En esta pila»:** filas con icono, nombre, `RoundIconButton(ic_remove)` «Quitar de la pila» y asa `ic_drag_indicator`. Acciones de TalkBack «Subir» y «Bajar», como hoy. Mantener una fila abre su Interior.
- **«Añadir»:** solo las apps que no están en la pila, cada una con `ic_add` al final. Un toque la añade.
- **Campo «Buscar apps»** abajo, pegado al teclado; filtra «Añadir».
- **«Quitar esta pila»:** `TextAction` secundario al final de la lista, con `UndoBar`.

«Convertir en pila» desde una app deja esa app como primera de la pila nueva.

### 6.3 Barra de búsqueda

- Fondo `Background` opaco. Sin el reloj atenuado.
- **Campo** abajo: `PillTextField`, relleno `Fill`, `heightIn(min = 56.dp)`, `Outset`. Sin borde, también al escribir. Con texto, `RoundIconButton(ic_close)` «Borrar».
- **Salidas**, 8 dp sobre el campo, fuera de la lista: a la izquierda, `Chip` con icono para el asistente (icono de su app), Web (`ic_search`) y Play Store (icono de su app); a la derecha, fijos, `RoundIconButton(ic_zones)` «Zonas» y `RoundIconButton(ic_settings)` «Ajustes de Alisio». La fila «Zonas» de la lista desaparece.
- **Lista** con `reverseLayout`, 8 dp sobre las salidas, con `contentPadding` superior igual a la barra de estado más 16 dp. El mejor resultado es la fila más baja y lleva `enterTarget`. Solo una fila lo lleva.
- **Etiquetas** solo en Personas, Apps, Acciones de apps, Ajustes y Recientes.
- **Cálculo:** una fila sin etiqueta con `ic_calculate`, la expresión en secundario y el resultado en `Numeral`. Enter copia, como hoy.
- **Reloj, gasto, portapapeles, preguntar al asistente:** una fila sin etiqueta con su vector (`ic_schedule`, `ic_receipt`, `ic_link`, icono de la app del asistente).
- **Portapapeles con la barra vacía:** Enter sigue abriendo el enlace; el título dice el dominio («Abrir example.org»).
- **Persona en la lista:** `Avatar` de 44 dp, nombre y, al final, sus canales. El canal principal es un `RoundIconButton` con `container = Accent` e icono `Background`; los demás, sin fondo. `contentDescription` con el nombre («Llamar a Laura»); el principal añade `stateDescription` «Principal».
- **Mantener una app** abre su Interior (sin la parte de hueco del dock).
- Importe y hora se formatean con el idioma y el formato de 12 o 24 h del sistema.

**Persona elegida** (sustituye a la lista, anclada abajo, sin caja):

- De arriba abajo: fila de cabecera con `RoundIconButton(ic_arrow_back)`, `Avatar` de 56 dp, nombre en `headlineSmall`, motivo en `bodyMedium` («Sueles usar SMS») y `RoundIconButton(ic_person)` «Abrir contacto».
- 20 dp debajo: `PrimaryButton` de ancho completo con icono y nombre del canal aprendido, y `ic_keyboard_return` al final del botón.
- 8 dp debajo: los demás canales como `TonalButton` con icono y texto, en `FlowRow` con 8 dp.
- Después, las salidas y el campo.

### 6.4 Zonas

- El contenido del Inicio se oculta. Queda el fondo de pantalla con `ZonesVeil`.
- **Cabecera** en el filo: «Zonas» en `headlineLarge` con sombra; 4 dp debajo, «Mantén una app para moverla» en `bodyMedium` con sombra. A la derecha, `RoundIconButton(ic_settings)` y `RoundIconButton(ic_add)` «Nueva isla». Se quitan «Pellizca para volver» y la pista del pie.
- **Isla:** placa `CardShape` con `Outset` y relleno 12 dp. Nombre en `SectionLabel` (sin `uppercase()`), 12 dp, y rejilla de 4 columnas con iconos de 52 dp y `DockLabel` (si `zonesLabels`). `PlateGap` entre islas. Se conserva el reparto actual de anchos (completo o media columna).
- **Isla propia:** el nombre es tocable y lleva `ic_edit` de 16 dp.
- **Isla vacía:** la misma placa, con «Suelta apps aquí» en `bodyMedium` `TextSecondary`, alto mínimo 48 dp.
- **Resto:** placa a todo el ancho; su índice `RailLetter` va fuera, a la derecha.
- **Arrastre:** como hoy, con háptica. La isla bajo el dedo lleva `selectedTile`; las imposibles bajan a α 0,38.
- **Mantener una app** abre su Interior; soltarla sin mover no cambia nada.
- **Hoja de isla** («Nueva isla» o su nombre): nombre como título editable, igual que en la pila; `AppPicker` múltiple para marcar apps; `PrimaryButton` «Crear» o «Guardar» con `enabled = name.isNotBlank()`. Tras «Crear», la hoja sigue abierta en la lista de apps. En una isla existente, `TextAction` secundario «Borrar isla» al final, con `UndoBar`.
- «Zonas» es la pantalla; cada grupo es una «isla», en todos los textos.

### 6.5 Interior de app (hoja)

- **Cabecera:** icono de 56 dp en el filo; nombre en `headlineSmall`; debajo, el uso de hoy en `bodyMedium` `TextSecondary`, o `TextAction` «Ver el uso de hoy» si falta el acceso; a la derecha, `RoundIconButton(ic_info)` «Información de la app».
- **«Accesos directos»:** filas con su icono de 44 dp. Sin el rol de inicio, una sola fila `ic_home` «Aplicación de inicio» con acción «Permitir».
- **Fila única, sin etiqueta:** `ic_swipe_up`, título «Al deslizar hacia arriba», secundario con el destino (o «Sin destino»), y acción «Elegir» o «Cambiar». El selector se abre en la misma hoja con `AppPicker` (grupo fijo: accesos directos de la app; y «Sin destino»). Solo en apps del dock.
- **«Isla»:** chips de elección en `FlowRow`.
- **«Hueco del dock»** (si se abrió desde el dock): fila `ic_swap_horiz` «Cambiar la app», fila `ic_stacks` «Convertir en pila» y, separada `GroupGap`, fila `ic_remove` «Dejar vacío» en `TextSecondary` con `UndoBar`.
- **«Poner en el dock»** (si se abrió desde Zonas, la barra o una pila): los seis huecos en miniatura (iconos de 44 dp, sin nombre) en una fila; un toque sustituye ese hueco, con `UndoBar`.
- **«Desinstalar»**, lo último y separado `GroupGap`: fila `ic_delete` en `TextSecondary` con el secundario «El teléfono te pedirá confirmarlo». Abre el diálogo del sistema; no hay `UndoBar`. En una app del sistema actualizada dice «Desinstalar actualizaciones». No sale en apps del sistema sin actualizar ni en las de otro perfil.

### 6.6 Pausa

- `Background` opaco. Contenido en el filo, anclado abajo.
- De arriba abajo: icono de la app de 56 dp; 32 dp; cuenta atrás en Space Grotesk Light con el tamaño actual; 16 dp; «Una pausa antes de …» en `headlineLarge`; 40 dp; `TextAction` «Abrir igualmente», centrado, a α 0,38 y desactivado hasta llegar a cero; 8 dp; `PrimaryButton` «No abrir» de ancho completo; 24 dp de margen inferior.
- Al llegar a cero, un anuncio único para TalkBack: «Ya puedes abrir …».

### 6.7 Bienvenida

- Nueve pasos en un `HorizontalPager` (`feature/onboarding/WelcomeScreen.kt`): saludo, estilo, barra, Trazo, inicial, Zonas, música, permisos e inicio. Arriba, «1 de 9» y «Saltar» en `Text` pleno; abajo, los puntos y un `PrimaryButton` de ancho completo.
- **Excepción a §2.7 (decisión del dueño):** el fondo se mueve solo. Tres manchas de color (la primera, el acento) derivan despacio sobre `Background` (`WelcomeBackground.kt`, una vuelta cada 24 s), con un velo hacia `Scrim` en la mitad de abajo. Con «Quitar animaciones» se quedan quietas.
- Las demostraciones son grabaciones reales del Inicio (`res/raw/welcome_*.mp4`), mudas y en bucle, con `CardShape` y un borde de `Text` α 0,16. Solo avanza la del paso que se ve, y se pausa fuera de la app; sin animaciones queda el primer fotograma.
- El primer paso abre con «Hola,» y el último con «Todo listo.» o «Ya casi está.», con el estilo de la hora y un degradado del acento a otro color; encogen para caber en una línea.
- El último paso repasa lo elegido (color, asistente, inicial, música): cada fila vuelve a su paso. Si Alisio no es el inicio, el botón lo pide y `TextAction` «Ahora no» acaba sin pedirlo.
- Acabar o saltar la bienvenida se guarda en `Prefs` y no viaja en la copia de ajustes. Se repite desde Ajustes → «Ver la bienvenida otra vez».

### 6.8 Aviso de accesibilidad

- `Background` opaco, con desplazamiento. `RoundIconButton(ic_arrow_back)` y título «Antes de activarlo» en `headlineLarge`.
- «Qué hace»: fila `ic_lock` «Bloquea con dos toques en el Inicio» y fila `ic_swipe_down` «Baja las notificaciones al deslizar».
- «Qué no hace»: fila `ic_visibility_off` con el texto actual acortado a dos líneas (no lee la pantalla, no escribe, sin Internet).
- Debajo, en `bodyMedium` `TextSecondary`, el aviso de las apps de banco y dónde encontrarlo en el sistema. No se quita ninguna garantía del texto actual.
- Al pie: `PrimaryButton` con la acción actual y `TextAction` «Ahora no».

### 6.9 Ajustes de Alisio

**Requisito del dueño, no negociable:** los ajustes son una portada con su menú de secciones y una subpágina por sección. Nunca un único listado largo con todas las opciones. Si algo de esta sección choca con eso, gana el requisito.

**Estructura.** Una portada y ocho subpáginas dentro de `SettingsScreen`, con `AnimatedContent` (`Standard`). Desde la fase 7, Iconos sale de Aspecto y Gestos sale de Inicio: con lo añadido, las dos páginas se habían vuelto listados largos. Atrás vuelve de la subpágina a la portada y de la portada al Inicio. Cada opción queda a dos toques. La barra indexa la portada y cada subpágina (`Target.AlisioSettings(destino)`, enum `SettingsDestination`) con sinónimos en los dos idiomas.

**Marco común.**

- Cabecera fija: `RoundIconButton(ic_arrow_back)` («Cerrar» en la portada, «Volver» en las subpáginas) y título en `headlineLarge`.
- **Vista previa viva** en la portada, en Aspecto y en Inicio: una ventana fija de 220 dp, sin fondo propio, arriba del todo. El Inicio real está oculto (§3, punto 6) y ahí se pinta el Inicio en miniatura con las mismas piezas: `WallpaperVeil`, reloj a 48 sp, Ahora de muestra, reproductor de muestra, dock real y píldora. Cambia en el acto con cada opción. Para ello, `DockItem`, `DockLabel` y `StackSlotIcon` salen de `HomeScreen.kt` a `ui/components` con un parámetro de escala; no se copian.
- Debajo de la ventana, el cuerpo: `Background` opaco con esquinas superiores `PillShape`, desplazable. Al desplazar, el cuerpo sube primero sobre la miniatura (como una hoja) hasta dejar a la vista la hora y su primera línea, y baja al volver arriba (`SheetCollapse`).
- En Gestos, Búsqueda, Zonas, Pausa y Permisos no hay ventana: el cuerpo ocupa toda la pantalla.
- Se abran desde donde se abran (barra, mantener el Inicio, Zonas, una sección buscada), debajo de la miniatura solo está el Inicio oculto: Zonas se retira mientras los ajustes están encima y vuelve con atrás.
- Si la ventana transparente no sale limpia, plan B: la misma miniatura sobre `Surface`, sin foto.

**Portada.** Ocho filas de 68 dp con vector, título y un resumen de lo elegido como secundario (hasta cuatro cosas), y al pie la copia de la configuración:

| Fila | Vector | Resumen de ejemplo |
| --- | --- | --- |
| Aspecto | `ic_palette` | «Turquesa · Original · Fondos que rotan» (o «Color del día») |
| Iconos | `ic_apps` | «A color · Iconos vivos · 2 iconos propios» |
| Inicio | `ic_home` | «6 huecos · Sugerencia · Notas · Reproductor» |
| Gestos | `ic_gesture` | «Dibujar la inicial · Pellizcar · Deslizar sobre el dock» |
| Búsqueda | `ic_search` | «Gemini · Portapapeles · Personas · Órdenes» |
| Zonas | `ic_zones` | «1 isla propia» |
| Pausa | `ic_hourglass` | «2 apps · 5 s» |
| Permisos | `ic_lock` | «Falta 1», «Lo que usas, permitido» o «Todo permitido» |

Si falta algún permiso que una opción encendida necesita, la fila Permisos sube al primer lugar. «Falta N» cuenta solo esos (`blockingAccess`); un acceso opcional sin conceder (Bluetooth, brillo, no molestar) no es un aviso.

**Iconos** (con vista previa): «Estilo» (A color, Monocromo, Del sistema y cada paquete), «Iconos vivos», «Iconos de apps», «Nombres en el dock» y «Nombres en Zonas».

**Gestos** (sin vista previa): «Dibujar la inicial» con su muestra, «Pellizcar para abrir Zonas», los dos gestos del servicio de accesibilidad con la fila que lo pide, «Dock» (deslizar sobre el dock y sus destinos) e «Índice de letras».

**Aspecto** (con vista previa). Desde la fase 7: fondo de pantalla, fondos que rotan, velo, acento (con el propio y el color del día) y tipografía; lo de iconos y nombres pasa a Iconos.

- Fila `ic_wallpaper` «Fondo de pantalla», con `ic_open_in_new` al final: abre `Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER))`.
- «Velo»: tres `PreviewChoice` de 96 × 120 dp. Cada una es una ventana al fondo real con su `WallpaperVeil` y «9:41» en `Numeral`. Nombres: Poco, Medio, Mucho.
- «Acento»: cuatro círculos de 36 dp, separados 16 dp, en 48 dp de toque. El elegido lleva `ic_check` en `Background`; ninguno cambia de tamaño. `contentDescription` con el nombre del color.
- «Iconos»: `PreviewChoice` en fila horizontal, una tesela por estilo (A color, Monocromo, Del sistema) y otra por paquete instalado. Cada una enseña las cuatro primeras apps del dock en ese estilo, en 2 × 2 a 40 dp. Los iconos se dibujan en `Dispatchers.IO` con una función de vista previa de `AppsRepository`, con caché en memoria.
- «Nombres en el dock»: dos `PreviewChoice` (tres iconos reales del dock con nombre y sin nombre).
- «Nombres en Zonas»: dos `PreviewChoice` (mini isla con nombre y sin nombre).

**Inicio** (con vista previa). Desde la fase 7, en este orden: Sugerencia, En curso y Audio Bluetooth; Hora; Junto a la hora (con Batería y Tiempo); Notas; Notificaciones; Personas sobre el dock; Dock (huecos). «Deslizar sobre el dock» y «Gestos» pasan a la página Gestos.

- Fila con interruptor «Sugerencia» (`ic_auto_awesome`). Debajo, fila «Audio Bluetooth» (`ic_bluetooth`): con permiso, el dispositivo conectado como secundario; sin él, acción «Permitir».
- Fila «Sugerencias silenciadas», solo si hay alguna: los iconos de esas apps en una fila; un toque devuelve una.
- Fila con interruptor «Reproductor» (`ic_play`).
- Fila con interruptor «Puntos de no leído» (`ic_notifications`). Debajo, la fila de apps del dock con su punto, como hoy: un toque enciende o apaga el punto de esa app.
- Fila con interruptor «Deslizar sobre el dock» (`ic_swipe_up`). Debajo, la fila de apps del dock, cada una con el icono de su destino en pequeño en la esquina; un toque abre el `AppPicker` de destino.
- «Gestos»: filas con interruptor «Pellizcar para Zonas» (`ic_pinch`), «Dos toques para bloquear» (`ic_lock`) y «Bajar las notificaciones» (`ic_swipe_down`).
- **Requisitos:** si falta el acceso a notificaciones, la primera fila del grupo es «Falta el acceso a notificaciones» con acción «Permitir», y Reproductor y Puntos quedan en «sin requisito». Lo mismo con el servicio de accesibilidad para los dos gestos (la acción abre el aviso de §6.8). Sale de generalizar `GestureRow` a una opción dependiente.

**Búsqueda:**

- Fila «Asistente» y fila «Búsqueda web»: icono y nombre de la app elegida, acción «Cambiar»; abren el `AppPicker` simple (grupo fijo: asistentes conocidos instalados, o «Que elija el sistema»).
- «Portapapeles»: fila con interruptor (`ic_link`), con el aviso actual como secundario de dos líneas.
- «Personas»: fila «Contactos» (`ic_contacts`) con `ic_check` o acción «Permitir». «Fotos de contacto»: dos `PreviewChoice` (un `Avatar` con la foto de la primera persona que tenga una, y otro con iniciales); sin el permiso, en «sin requisito».
- **Gasto rápido: eliminado por decisión del dueño** (usa su propia app). No se construye su sección en los ajustes ni se rediseña su fila de la barra. La función está borrada entera (lógica, preferencia, fila de la barra, vector `ic_receipt` y textos); que falte no es una función perdida.

**Zonas:**

- Fila `ic_zones` «Ver Zonas».
- «Islas propias»: una fila por isla (`ic_edit` al final; abre la hoja de isla) y fila `ic_add` «Nueva isla».
- La opción «Nombres en Zonas» vive en Aspecto.

**Pausa:**

- Una línea de ayuda: «Solo al abrirlas desde Alisio».
- Fila de teselas de app: icono de 52 dp con `DockLabel`; un toque la quita, con `UndoBar`. La última tesela es `ic_add` «Añadir» y abre el `AppPicker` múltiple.
- «Duración»: chips de elección 3 s, 5 s y 10 s; en la misma línea, a la derecha, `RoundIconButton(ic_play)` «Ver la pausa».

**Permisos:**

- Una fila por acceso, con su vector y una línea sobre para qué sirve: Aplicación de inicio, Contactos, Calendario, Ubicación aproximada, Audio Bluetooth, Acceso a notificaciones, Acceso a uso, Servicio de accesibilidad, Cambiar ajustes del sistema y No molestar.
- Concedido: `ic_check` en `TextSecondary`, sin acento. Tocar la fila abre el sistema.
- Pendiente: acción «Permitir». Los que se conceden en una pantalla del sistema llevan `ic_open_in_new` de 16 dp junto al título.
- Los pendientes van primero.

## 7. Textos

- Siempre en `res/values/strings.xml` y `res/values-es/strings.xml`. Etiquetas con mayúscula solo inicial.
- Un término por idioma: «Inicio» / «Home screen»; «isla» / «island»; «Zonas» / «Zones» solo para la pantalla.
- Los ajustes tienen sus propios textos de sección; no toman prestados los de la barra ni los de Ahora.
- Al quitar un uso, se busca el recurso en Kotlin y en XML y se borra de los dos archivos: lint no lo detecta hoy.

## 8. Fuera de esta guía

- Elegir tipografía, icono por app e imagen para una pila: hecho en las fases 6 y 7 (ver `docs/PLAN.md` §11). El icono elegido y la imagen van recortados a la forma de `DockIconShape`, sin caja alrededor.
- Acento propio (fase 7): un quinto círculo junto a los cuatro de la paleta abre el editor bajo ellos. Texto sobre el acento: `Background`, con aviso por debajo de 4,5:1.
- `error += "UnusedResources"` en lint: lo decide el dueño.

## 9. Orden de aplicación

1. Tokens y componentes (`ui/theme`, `ui/components`).
2. Inicio y legibilidad.
3. Barra y personas.
4. Hojas e Interior de app.
5. Zonas.
6. Ajustes.

Cada paso termina con la build en verde y capturas del emulador. Lo que cambie respecto a la §6 de `docs/PLAN.md` se apunta en su §11.

## 10. Componentes

Lo que existe en el código tras el paso 1 (fundamentos). Antes de crear algo, se busca aquí y se extiende.

### 10.1 Tokens (`ui/theme`)

`AlisioTokens` (`Theme.kt`):

| Grupo | Nombres |
| --- | --- |
| Color | `Background`, `Surface`, `Text`, `TextSecondary`, `TextTertiary`, `Fill`, `Plate`, `ZonesVeil`, `Scrim`, `Accents`, `Accent`, `AccentSoft`, `DISABLED_ALPHA` (0,38) |
| Espaciado | `Edge` 24, `Outset` 12, `IconSlot` 44, `IconGap` 16, `RowMinHeight` 56, `GroupGap` 28, `LabelGap` 8, `TitleGap` 24, `SheetTitleGap` 20, `PlateGap` 12, `MinTouch` 48, `InfoRow` 32 (renglones bajo la hora), `DockIconSize` 52 |
| Radios | `PillShape`, `CardShape`, `RowShape`, `DockIconShape`, `SheetShape` (esquinas superiores de hoja) |
| Tipografía propia | `Clock`, `Numeral`, `SectionLabel`, `DockLabel`, `RailLetter`. Son propiedades `@Composable`: leen la familia elegida |

- Ranuras de Material (`MaterialTheme.typography`): `headlineLarge` (título de pantalla), `headlineSmall` (título de hoja), `titleMedium` (título de fila), `bodyLarge`, `bodyMedium`, `labelLarge`.
- `TextStyle.onWallpaper()`: texto directo sobre el fondo de pantalla (claro, opaco, con la sombra única). `OnWallpaperShadow` no es un token suelto porque el desenfoque depende de la densidad.
- Se borraron `SurfaceElevated`, `CardBorder` y `ControlBorder`.
- `AlisioTheme(accent, font)`: fija acento, familias (`LocalAlisioFonts`) y el pulsado (`Text` al 8 %) para toda la app.
- `FontChoice` (`core/theme`): `PAIR`, `INTER`, `GROTESK`, `SYSTEM`. `fontsFor(choice)` da las familias de una elección para dibujar su vista previa. Se guarda en `Prefs.font` (`AlisioSettings.setFont`).
- `AlisioMotion` (`AlisioMotion.kt`): `fast()`, `standard()`, `screen()`, `exit()`, `follow()`, `Rise`, `screenEnter()`, `screenExit()`.

### 10.2 Componentes (`ui/components`)

| Archivo | Componente | Para qué |
| --- | --- | --- |
| `Rows.kt` | `ItemRow` | La única fila. Inicio: `icon`, `avatarName` (con `icon` como foto) o `leadingIcon`. Final: `checked` (interruptor), `selected` (+ `multiple`), `enterTarget` o `trailing` (verbo). También `subtitleLines`, `enabled`, `secondary` (destructivo), `onLongClick`, `actions` |
| `Rows.kt` | `SectionLabel(text, first)` | Etiqueta de grupo con su aire y `heading()` |
| `Rows.kt` | `Note`, `AppIcon` | Texto de apoyo alineado con las filas; icono de app |
| `Avatar.kt` | `Avatar(name, size, photo)` | Persona: foto o iniciales sobre `Fill` |
| `Buttons.kt` | `PrimaryButton`, `TonalButton`, `TextAction(secondary)`, `RoundIconButton(iconSize, container)`, `Chip(icon, appIcon, selected)` | `Chip` con `selected = null` es una salida; con valor, una opción |
| `Fields.kt` | `PillTextField(leadingIcon, maxLines)`, `TitleTextField` | Campo en píldora; nombre editable como título de hoja |
| `Sheet.kt` | `AlisioSheet(onDismiss, expanded)`, `SheetTitle(text, onBack)` | Hoja inferior y su título (con volver en un segundo paso) |
| `Preview.kt` | `PreviewChoice`, `Modifier.selectedTile(selected, shape)`, `Modifier.selectedTileSegment(selected, top, bottom)` | Tesela de vista previa; anillo de elegido o de destino; el mismo anillo para una placa pintada por tramos (Resto) |
| `AppPicker.kt` | `AppPicker`, `AppPickerSheet` | Selector de apps genérico (clave, nombre, icono), simple o `multiple`, con grupo fijo `pinned` |
| `UndoBar.kt` | `UndoBar`, `rememberUndoState`, `LocalUndo`, `UndoState.show(message, onUndo)` | Deshacer. La actividad pone una sola barra; las pantallas usan `LocalUndo.current` |
| `Tiles.kt` | `IconLabel(onWallpaper)`, `VectorTileIcon`, `SymbolTileIcon`, `SymbolTileField`, `FolderTileIcon` | Nombre bajo icono; placas con forma de icono (hueco vacío, símbolo y carpeta de pila); el símbolo mientras se escribe |
| `Wallpaper.kt` | `Modifier.wallpaperVeil(dim, lightWallpaper)`, `rememberLightWallpaper()` | Velo único; usa `veilStops` de `core/wallpaper` |
| `UnreadDot.kt`, `Time.kt`, `PinchGesture.kt` | sin cambios | |

En `feature/appmenu`: `AppMenuSheet` es el único Interior de app y lo pinta siempre el Inicio (`HomeScreen(menuRequest)`), también el que piden Zonas y la barra. `DockPlacement` le da los huecos de «Poner en el dock». En `feature/zones`: `ZoneEditSheet(edit, viewModel, onDismiss)` es la hoja de una isla, con su `AppPicker` múltiple; la usan Zonas y los ajustes.

En `feature/home`: `StackSlotIcon(stack, size)` y `DockSlotIcon(slot, size)` son `internal` y se escalan, para las vistas previas. `HomePreview` es el Inicio en miniatura de los ajustes: compone `NowCard`, `PlayerBar`, `DockRow` y `SearchPill` con la densidad reducida. La parte visual de un icono del dock es `DockIcon` (`ui/components/Tiles.kt`: icono, punto, marca de deslizar y nombre); `DockItem` solo le añade los gestos.

En `feature/settings`: `SettingsScreen(page, …)` es el marco (ventana con la miniatura, cabecera fija y página); cada página está en su archivo. `DependentSwitch` y `RequirementRow` son la opción que depende de un acceso y la fila que lo pide; `AppGrid` y `AppTile`, una rejilla de apps con el aspecto del dock. En `feature/appmenu`, `SwipeTargetSheet` es el selector de destino al deslizar en su propia hoja. `ItemRow` tiene además `titleMark`, `numeral` y `onClickLabel`; `PreviewChoice`, `enabled` y `backdrop`; `Modifier.wallpaperVeil`, `overHome`.

Desviaciones de §6.9 vistas en el emulador: la ventana de la miniatura mide 292 dp (con 220 no caben todas las piezas) y su hora queda en unos 42 sp; la cabecera de cada página va bajo la ventana, sobre el fondo opaco; las teselas del velo pintan el velo sobre un gris de muestra, porque leer el fondo real pide un permiso.

### 10.3 Deshacer ya conectado

`HomeViewModel.clearDockSlot` y `setDockApp` devuelven la orden de deshacer; `dismissNow` devuelve `NowDismissal(silenced, undo)`; `ZonesViewModel.deleteZone` devuelve la orden de deshacer; `AlisioSettings.addPauseFor` repone una pausa.

### 10.4 Vectores

Añadidos en `res/drawable`: `ic_close`, `ic_search`, `ic_settings`, `ic_check`, `ic_drag_indicator`, `ic_keyboard_return`, `ic_calculate`, `ic_contacts`, `ic_auto_awesome`, `ic_stacks`, `ic_swap_horiz`, `ic_palette`, `ic_wallpaper`, `ic_open_in_new`, `ic_help`, `ic_visibility_off`, `ic_home`, `ic_text_fields`, `ic_delete` (borrar isla). «Quitar» (de la pila, dejar vacío un hueco, quitar el destino) usa `ic_close`, la misma cruz que vacía un campo; `ic_delete` queda para lo que se borra de verdad. Se borró `ic_remove`: su guion se leía como el carácter «—». Aún sin uso (y sin ver en pantalla): `ic_wallpaper`, `ic_open_in_new`, `ic_visibility_off`. `ic_home` lo usa el Interior de app cuando falta el rol de inicio (sin ver en pantalla).

### 10.5 Desviaciones de esta guía vistas en el emulador

- Zonas (§6.4): la hoja de una isla no tiene «Guardar»: el nombre se guarda al escribirlo, como el de una pila, y cada app al marcarla. «Crear» solo existe mientras la isla nueva no tiene nombre. «Borrar isla» va en el grupo fijo de arriba del selector, como «Dejar vacío» en el hueco del dock.
- Zonas (§6.4): las celdas de una isla miden 72 dp y se reparten de borde a borde, para que el primer icono caiga en el filo y el último a la misma distancia del otro lado. La lista acaba en la barra de estado: las islas no pasan por debajo de la hora.
- Interior de app (§6.5): con destino elegido, la fila «Al deslizar hacia arriba» enseña el icono del destino en lugar de `ic_swipe_up`. En «Poner en el dock», tocar una pila mete la app en ella en vez de sustituirla; la hoja se cierra y la barra de deshacer dice dónde ha quedado.
- `tnum` no se aplica en `Numeral`: no se ha comprobado que `space_grotesk.ttf` lo traiga.
- Pausa (§6.6): la cuenta atrás va antes que el icono. Con el icono lo primero, la cuenta apagada dejaba un hueco entre el icono y el título.
- Hoja de una pila (§6.2): mantener una fila de «En esta pila» no abre su Interior (sería una hoja sobre otra). Sí lo abre mantener una app en la pila abierta.
- Hueco del dock (§6.2): «Dejar vacío» va en el grupo fijo de arriba, no al final de la lista: la lista crece desde el buscador y lo que borra queda lejos del pulgar.
- Barra (§6.3): Zonas y los ajustes de Alisio no van a la derecha de las salidas, sino en el extremo del campo mientras está vacío (`PillTextField(emptyActions)`). Tres salidas con icono y dos botones de 48 dp no caben en 384 dp, y las salidas tendrían que pasar a dos líneas.
- Barra (§6.3): el canal principal de WhatsApp va relleno de su verde, no de acento: el dibujo de su llamada es el mismo que el de la llamada normal.
- Barra (§6.3): la lista deja 16 dp arriba y 8 dp abajo, no «la barra de estado más 16 dp»: la pantalla ya descuenta la barra de estado.
- Con escala de fuente 1,3 los nombres del dock se cortan («YouT…»): el hueco mide 60 dp para que el primer icono caiga en el filo.
- Pausa (§6.9): cada app elegida conserva la insignia pequeña con `ic_close` en su esquina. Sin ella, un icono con su nombre parece que abre la app y no que la quita. La ayuda queda en una línea, como pide la guía.
- Interior de app (§6.5): con un acceso directo como destino, la fila enseña el icono propio del acceso si está cargado y, si no, `ic_swipe_up`; nunca el de su app, que repetiría el de la cabecera.
- Zonas (§6.4): las islas usan `Outset` a los dos lados; solo Resto deja a su derecha el ancho del índice. El índice sale solo con Resto a la vista y empieza a su altura; si Resto asoma tan abajo que sus letras no caben a 9 dp, el índice espera a que se desplace (fase 7: antes subía y quedaba encima de las islas de arriba).
