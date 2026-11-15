package dev.jesusmolano.alisio.core.zones

import dev.jesusmolano.alisio.core.text.normalize

/**
 * Una isla de Zonas (§5.7). Diario y las pilas salen del dock, Sistema de las apps predeterminadas y
 * Resto de lo que sobra; Dev y las propias solo tienen lo que se les asigna a mano.
 */
sealed interface ZoneId {
    /** Las apps sueltas del dock. */
    data object Daily : ZoneId

    /** La pila que ocupa el hueco [slot] del dock. */
    data class Stack(val slot: Int) : ZoneId

    data object Dev : ZoneId

    /** Isla creada por el usuario; [id] es su fila en la base de datos. */
    data class Custom(val id: Long) : ZoneId

    data object System : ZoneId

    data object Rest : ZoneId

    /**
     * Si una app se puede asignar a mano a esta isla. A Diario y a las pilas no: reflejan el dock, y
     * una app solo entra en ellas entrando en el dock.
     */
    val assignable: Boolean get() = this is Dev || this is Custom || this is System || this is Rest

    /** Lo que se guarda en la asignación de una app. */
    fun encode(): String = when (this) {
        Daily -> "daily"
        is Stack -> "stack:$slot"
        Dev -> "dev"
        is Custom -> "custom:$id"
        System -> "system"
        Rest -> "rest"
    }

    companion object {
        fun parse(value: String): ZoneId? = when {
            value == "daily" -> Daily
            value == "dev" -> Dev
            value == "system" -> System
            value == "rest" -> Rest
            value.startsWith("stack:") -> value.substring(6).toIntOrNull()?.let(::Stack)
            value.startsWith("custom:") -> value.substring(7).toLongOrNull()?.let(::Custom)
            else -> null
        }
    }
}

/** Lo que Zonas necesita saber de una app. [key] es su `AppKey` como texto. */
data class ZoneApp(val key: String, val label: String, val packageName: String)

/** Una pila del dock: su hueco, su nombre y sus apps. */
data class ZoneStack(val slot: Int, val name: String, val appKeys: List<String>)

/** Isla propia, con el nombre que le ha puesto el usuario. */
data class CustomZone(val id: Long, val name: String)

/**
 * @param dockApps claves de las apps sueltas del dock.
 * @param stacks las pilas del dock, en el orden de sus huecos.
 * @param custom las islas propias, en su orden.
 * @param assignments isla elegida a mano por clave de app.
 * @param systemPackages paquetes de Ajustes y de las apps predeterminadas del sistema.
 * @param frecency frecencia por clave de app, ya calculada para este momento.
 */
class ZoneInput(
    val apps: List<ZoneApp>,
    val dockApps: List<String> = emptyList(),
    val stacks: List<ZoneStack> = emptyList(),
    val custom: List<CustomZone> = emptyList(),
    val assignments: Map<String, ZoneId> = emptyMap(),
    val systemPackages: Set<String> = emptySet(),
    val frecency: Map<String, Double> = emptyMap()
)

/** Una isla con sus apps ya ordenadas. [name] solo lo llevan las pilas y las islas propias. */
data class Zone(val id: ZoneId, val name: String?, val apps: List<ZoneApp>)

/** Decide en qué isla va cada app. Cada app está en una sola isla. */
class ZoneResolver(private val input: ZoneInput) {
    private val dockApps = input.dockApps.toSet()

    // Una app repetida en dos pilas se queda en la primera.
    private val stackOf: Map<String, Int> =
        buildMap { input.stacks.forEach { stack -> stack.appKeys.forEach { putIfAbsent(it, stack.slot) } } }
    private val customIds = input.custom.mapTo(HashSet()) { it.id }

    /** La isla que le toca a la app sin asignación: la del dock, Sistema o Resto. */
    fun defaultZone(app: ZoneApp): ZoneId {
        val stack = stackOf[app.key]
        return when {
            app.key in dockApps -> ZoneId.Daily
            stack != null -> ZoneId.Stack(stack)
            app.packageName in input.systemPackages -> ZoneId.System
            else -> ZoneId.Rest
        }
    }

    /**
     * La isla en la que está la app. Lo asignado a mano gana a lo calculado, también para una app
     * del dock. Una asignación a una isla que ya no existe no cuenta.
     */
    fun zoneOf(app: ZoneApp): ZoneId {
        val assigned = input.assignments[app.key]
        val valid = assigned != null && assigned.assignable && (assigned !is ZoneId.Custom || assigned.id in customIds)
        return if (valid) assigned else defaultZone(app)
    }
}

/**
 * Reparte las apps en islas. El orden de las islas es fijo (§5.7: no cambian de sitio solas): Diario,
 * las pilas, Dev, las propias, Sistema y Resto. Dev, las pilas y las propias salen aunque estén
 * vacías, porque son un sitio donde soltar apps. Diario y Sistema vacías solo salen si a alguna app
 * le tocan por defecto: esa app está en otra isla por una asignación a mano, y la única forma de
 * olvidarla es llevarla de vuelta (`assignmentChange` da `Clear` solo para la isla por defecto).
 * Dentro de cada isla se ordena por frecencia; Resto va de la A a la Z.
 */
fun buildZones(input: ZoneInput, resolver: ZoneResolver = ZoneResolver(input)): List<Zone> {
    val byZone = input.apps.groupBy(resolver::zoneOf)
    val defaults = input.apps.mapTo(HashSet(), resolver::defaultZone)

    fun zone(id: ZoneId, name: String? = null) = Zone(id, name, byZone[id].orEmpty().byFrecency(input.frecency))

    fun fixed(id: ZoneId) = zone(id).takeIf { it.apps.isNotEmpty() || id in defaults }

    return buildList {
        fixed(ZoneId.Daily)?.let(::add)
        input.stacks.forEach { add(zone(ZoneId.Stack(it.slot), it.name)) }
        add(zone(ZoneId.Dev))
        input.custom.forEach { add(zone(ZoneId.Custom(it.id), it.name)) }
        fixed(ZoneId.System)?.let(::add)
        add(Zone(ZoneId.Rest, null, byZone[ZoneId.Rest].orEmpty().alphabetically()))
    }
}

/** De más a menos frecencia; en empate (lo habitual en apps sin abrir), de la A a la Z. */
private fun List<ZoneApp>.byFrecency(frecency: Map<String, Double>): List<ZoneApp> =
    alphabetically().sortedByDescending {
        frecency[it.key] ?: 0.0
    }

private fun List<ZoneApp>.alphabetically(): List<ZoneApp> =
    sortedWith(compareBy({ normalize(it.label) }, { it.label }, { it.key }))

/** Qué hay que guardar para llevar una app a una isla. */
sealed interface AssignmentChange {
    /** La isla es la que le toca sin asignación: basta con olvidar la que tuviera. */
    data object Clear : AssignmentChange

    data class Assign(val zone: ZoneId) : AssignmentChange

    /** No se puede: a Diario o a una pila solo se entra desde el dock. */
    data object Invalid : AssignmentChange
}

/** @param default la isla que le toca a la app sin asignación (`ZoneResolver.defaultZone`). */
fun assignmentChange(target: ZoneId, default: ZoneId): AssignmentChange = when {
    target == default -> AssignmentChange.Clear
    target.assignable -> AssignmentChange.Assign(target)
    else -> AssignmentChange.Invalid
}

/** Las islas a las que se puede llevar una app que está en [current], en el orden de [zones]. */
fun moveTargets(zones: List<ZoneId>, current: ZoneId, default: ZoneId): List<ZoneId> =
    zones.filter { it != current && assignmentChange(it, default) != AssignmentChange.Invalid }
