package dev.jesusmolano.alisio.core.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    private val forecast =
        """
        {"latitude":40.42,"longitude":-3.7,"current_units":{"temperature_2m":"°C"},
         "current":{"time":"2026-11-16T10:00","interval":900,"temperature_2m":12.4,"weather_code":61,"is_day":1},
         "daily":{"time":["2026-11-16"],"temperature_2m_max":[15.1],"temperature_2m_min":[6.8]}}
        """.trimIndent()

    @Test
    fun `lee la temperatura, el cielo y la máxima y mínima de hoy`() {
        val reading = parseForecast(forecast, at = 1_000L, latitude = 40.4168, longitude = -3.7038)!!
        assertEquals(12.4, reading.temperature, 0.0)
        assertEquals(WeatherKind.RAIN, reading.kind)
        assertTrue(reading.day)
        assertEquals(15.1, reading.max!!, 0.0)
        assertEquals(6.8, reading.min!!, 0.0)
        // Se guarda el sitio ya redondeado: es lo que se pidió.
        assertEquals(40.42, reading.latitude, 0.0)
        assertEquals(-3.7, reading.longitude, 0.0)
    }

    @Test
    fun `sin temperatura o sin código no hay lectura`() {
        assertNull(parseForecast("""{"current":{"weather_code":0}}""", 0L, 0.0, 0.0))
        assertNull(parseForecast("""{"current":{"temperature_2m":3}}""", 0L, 0.0, 0.0))
        assertNull(parseForecast("no es json", 0L, 0.0, 0.0))
        assertNull(parseForecast("""{"error":true,"reason":"x"}""", 0L, 0.0, 0.0))
    }

    @Test
    fun `de noche y sin diarios también vale`() {
        val reading = parseForecast("""{"current":{"temperature_2m":-1,"weather_code":0,"is_day":0}}""", 0L, 0.0, 0.0)!!
        assertFalse(reading.day)
        assertNull(reading.max)
    }

    @Test
    fun `agrupa los códigos WMO en lo que se dibuja`() {
        assertEquals(WeatherKind.CLEAR, weatherKind(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, weatherKind(2))
        assertEquals(WeatherKind.CLOUDY, weatherKind(3))
        assertEquals(WeatherKind.FOG, weatherKind(48))
        assertEquals(WeatherKind.DRIZZLE, weatherKind(55))
        assertEquals(WeatherKind.RAIN, weatherKind(81))
        assertEquals(WeatherKind.SNOW, weatherKind(86))
        assertEquals(WeatherKind.STORM, weatherKind(99))
        assertEquals(WeatherKind.CLOUDY, weatherKind(42))
    }

    @Test
    fun `se vuelve a leer a la media hora o en otro sitio`() {
        val saved = parseForecast(forecast, at = 0L, latitude = 40.42, longitude = -3.70)!!
        assertTrue(weatherIsStale(null, 0L, 40.42, -3.70))
        assertFalse(weatherIsStale(saved, WEATHER_MAX_AGE_MILLIS - 1, 40.42, -3.70))
        assertTrue(weatherIsStale(saved, WEATHER_MAX_AGE_MILLIS, 40.42, -3.70))
        // Moverse dentro de la misma celda de dos decimales no pide otra lectura; salir de ella, sí.
        assertFalse(weatherIsStale(saved, 1L, 40.4249, -3.6951))
        assertTrue(weatherIsStale(saved, 1L, 41.39, 2.17))
        // Un reloj que va hacia atrás tampoco deja una lectura «del futuro» para siempre.
        assertTrue(weatherIsStale(saved.copy(at = 10_000L), 0L, 40.42, -3.70))
    }

    @Test
    fun `la petición lleva coordenadas redondeadas con punto decimal`() {
        val url = forecastUrl(40.416775, -3.703790)
        assertTrue(url, url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=40.42&longitude=-3.70&"))
        assertTrue(url.contains("current=temperature_2m,weather_code"))
        assertTrue(url.contains("daily=temperature_2m_max,temperature_2m_min"))
        assertTrue(url.contains("timezone=auto"))
    }

    @Test
    fun `la búsqueda de ciudad codifica el nombre`() {
        val url = geocodingUrl(" A Coruña ", "es")
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=A+Coru%C3%B1a&count=6&language=es&format=json",
            url
        )
    }

    @Test
    fun `lee los sitios encontrados con su región`() {
        val json =
            """
            {"results":[
              {"id":1,"name":"Mérida","latitude":38.91,"longitude":-6.34,"country":"España","admin1":"Extremadura"},
              {"id":2,"name":"Mérida","latitude":20.97,"longitude":-89.62,"country":"México","admin1":"Yucatán"},
              {"id":3,"name":"Sin sitio"}
            ],"generationtime_ms":0.5}
            """.trimIndent()
        val places = parseGeocoding(json)
        assertEquals(2, places.size)
        assertEquals(Place("Mérida", "Extremadura, España", 38.91, -6.34), places[0])
        assertEquals("Yucatán, México", places[1].region)
        assertEquals(emptyList<Place>(), parseGeocoding("""{"generationtime_ms":0.1}"""))
        assertEquals(emptyList<Place>(), parseGeocoding("<html>"))
    }
}
