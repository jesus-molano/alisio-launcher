package dev.jesusmolano.alisio.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import dev.jesusmolano.alisio.core.icons.appFilterComponent
import org.xmlpull.v1.XmlPullParser

/** Paquete de iconos instalado, de los que siguen el formato de ADW y Nova (`appfilter.xml`). */
class IconPackInfo(val packageName: String, val label: String)

private const val TAG = "IconPacks"

// Los paquetes se anuncian con una actividad que atiende alguna de estas acciones.
private val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")

/** Paquetes de iconos instalados. Necesita las acciones de [THEME_ACTIONS] en `<queries>`. */
fun installedIconPacks(context: Context): List<IconPackInfo> {
    val packageManager = context.packageManager
    return THEME_ACTIONS
        .flatMap { packageManager.queryIntentActivities(Intent(it), 0) }
        .distinctBy { it.activityInfo.packageName }
        .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
        .sortedBy { it.label.lowercase() }
}

/**
 * Iconos de un paquete: su `appfilter.xml` dice qué dibujo corresponde a cada actividad de inicio.
 * Si el paquete se ha desinstalado o no se puede leer, no cubre ninguna app.
 */
class IconPack private constructor(
    private val packageName: String,
    private val resources: Resources,
    private val drawableByComponent: Map<String, String>
) {
    fun iconFor(component: ComponentName): Drawable? {
        val name = drawableByComponent[component.flattenToString()] ?: return null

        // Los paquetes nombran sus dibujos en el XML: no hay otra forma de llegar a ellos.
        @Suppress("DiscouragedApi")
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return try {
            resources.getDrawable(id, null)
        } catch (e: Resources.NotFoundException) {
            Log.w(TAG, "El paquete $packageName no tiene el dibujo $name", e)
            null
        }
    }

    companion object {
        fun load(context: Context, packageName: String): IconPack? = try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            IconPack(packageName, resources, readAppFilter(resources, packageName))
        } catch (e: Exception) {
            // Desinstalado, o con un appfilter.xml que no se deja leer: se sigue sin paquete.
            Log.w(TAG, "No se ha podido leer el paquete de iconos $packageName", e)
            null
        }

        /** El filtro puede venir como recurso XML o como archivo en `assets`. */
        private fun readAppFilter(resources: Resources, packageName: String): Map<String, String> {
            @Suppress("DiscouragedApi")
            val id = resources.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) return resources.getXml(id).use(::parseAppFilter)
            return resources.assets.open("appfilter.xml").use { stream ->
                parseAppFilter(Xml.newPullParser().apply { setInput(stream, null) })
            }
        }

        /** Lee los `<item component="ComponentInfo{paquete/actividad}" drawable="nombre"/>`. */
        private fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
            val result = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                appFilterComponent(component)?.let { result.putIfAbsent(it, drawable) }
            }
            return result
        }
    }
}
