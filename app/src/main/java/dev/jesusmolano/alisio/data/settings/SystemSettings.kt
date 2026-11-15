package dev.jesusmolano.alisio.data.settings

import android.provider.Settings
import androidx.annotation.StringRes
import dev.jesusmolano.alisio.R

/** Pantalla de Ajustes del sistema que la barra puede abrir. Los sinónimos solo sirven para encontrarla. */
class SystemSetting(val action: String, @param:StringRes val label: Int, synonyms: String) {
    val key: String get() = "setting:$action"
    val synonyms: List<String> = synonyms.split(',').map(String::trim).filter(String::isNotEmpty)
}

/**
 * Lista fija de §5.3: intents de `Settings`. El nombre sale en el idioma del sistema; los sinónimos
 * van en español y en inglés, separados por comas, para encontrarlos escribiendo en cualquiera de los dos.
 */
val SYSTEM_SETTINGS: List<SystemSetting> =
    listOf(
        SystemSetting(Settings.ACTION_SETTINGS, R.string.setting_all, "ajustes, configuración, settings"),
        SystemSetting(Settings.ACTION_WIFI_SETTINGS, R.string.setting_wifi, "wifi, red, internet, network"),
        SystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, R.string.setting_bluetooth, "auriculares, headphones"),
        SystemSetting(
            Settings.ACTION_DISPLAY_SETTINGS,
            R.string.setting_display,
            "pantalla, brillo, modo oscuro, display, brightness, dark mode"
        ),
        SystemSetting(
            Settings.ACTION_SOUND_SETTINGS,
            R.string.setting_sound,
            "sonido, volumen, vibración, tono, sound, volume, ringtone"
        ),
        SystemSetting(
            Settings.ACTION_BATTERY_SAVER_SETTINGS,
            R.string.setting_battery,
            "batería, ahorro de energía, carga, battery, power saving, charging"
        ),
        SystemSetting(
            Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            R.string.setting_storage,
            "almacenamiento, memoria, espacio, storage, memory, space"
        ),
        SystemSetting(
            Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
            R.string.setting_apps,
            "aplicaciones, desinstalar, applications, uninstall"
        ),
        SystemSetting(
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            R.string.setting_default_apps,
            "predeterminadas, aplicación de inicio, launcher, default apps, home app"
        ),
        SystemSetting(
            Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            R.string.setting_location,
            "ubicación, gps, localización, location"
        ),
        SystemSetting(
            Settings.ACTION_SECURITY_SETTINGS,
            R.string.setting_security,
            "seguridad, bloqueo, huella, security, lock, fingerprint"
        ),
        SystemSetting(
            Settings.ACTION_PRIVACY_SETTINGS,
            R.string.setting_privacy,
            "privacidad, permisos, privacy, permissions"
        ),
        SystemSetting(
            Settings.ACTION_DATE_SETTINGS,
            R.string.setting_date,
            "fecha, hora, zona horaria, date, time, time zone"
        ),
        SystemSetting(
            Settings.ACTION_LOCALE_SETTINGS,
            R.string.setting_language,
            "idioma, lengua, región, language, region"
        ),
        SystemSetting(
            Settings.ACTION_INPUT_METHOD_SETTINGS,
            R.string.setting_keyboard,
            "teclado, escritura, keyboard, typing"
        ),
        SystemSetting(
            Settings.ACTION_ACCESSIBILITY_SETTINGS,
            R.string.setting_accessibility,
            "accesibilidad, tamaño de letra, accessibility, font size"
        ),
        SystemSetting(
            Settings.ACTION_DATA_ROAMING_SETTINGS,
            R.string.setting_mobile_data,
            "datos móviles, roaming, sim, mobile data"
        ),
        SystemSetting(
            Settings.ACTION_DATA_USAGE_SETTINGS,
            R.string.setting_data_usage,
            "uso de datos, consumo de datos, data usage"
        ),
        SystemSetting(
            Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            R.string.setting_airplane,
            "modo avión, modo vuelo, airplane mode, flight mode"
        ),
        SystemSetting(Settings.ACTION_NFC_SETTINGS, R.string.setting_nfc, "nfc, pago sin contacto, contactless"),
        SystemSetting(Settings.ACTION_VPN_SETTINGS, R.string.setting_vpn, "vpn"),
        SystemSetting(
            Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            R.string.setting_developer,
            "desarrollador, depuración usb, adb, developer, usb debugging"
        ),
        SystemSetting(
            Settings.ACTION_DEVICE_INFO_SETTINGS,
            R.string.setting_about,
            "acerca del teléfono, versión, one ui, about phone, version"
        )
    )
