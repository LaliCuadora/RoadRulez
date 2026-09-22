package com.tupackage.radardenormativas.data.remote;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Servicio de geocodificación inversa usando Nominatim (OpenStreetMap).
 *
 * Dado un par de coordenadas GPS devuelve el código INE del municipio
 * en el que se encuentra el usuario.
 *
 * Nominatim es gratuito y no requiere API key.
 * Límite: 1 petición/segundo. Para uso normal de la app es más que suficiente.
 *
 * IMPORTANTE: Siempre ejecutar en un hilo background, nunca en Main Thread.
 */
public class GeocodingService {

    private static final String TAG = "GeocodingService";

    // Endpoint de geocodificación inversa de Nominatim
    // format=json → respuesta en JSON
    // addressdetails=1 → incluye desglose de dirección (municipio, provincia...)
    // extratags=1 → necesario para obtener el código ref:INE
    // zoom=10 → nivel municipio (no calle, no país)
    private static final String URL_NOMINATIM =
            "https://nominatim.openstreetmap.org/reverse" +
                    "?format=json&addressdetails=1&extratags=1&zoom=10&accept-language=es";

    // User-Agent obligatorio por política de Nominatim
    // Sin él bloquea las peticiones
    private static final String USER_AGENT = "RadarDeNormativas/1.0";

    /**
     * Resultado de la geocodificación.
     * Contiene el código INE si se encontró, o null si falló.
     */
    public static class ResultadoGeocodificacion {
        public final String codigoIne;
        public final String nombreMunicipio;
        public final String provincia;

        public ResultadoGeocodificacion(String codigoIne,
                                        String nombreMunicipio,
                                        String provincia) {
            this.codigoIne       = codigoIne;
            this.nombreMunicipio = nombreMunicipio;
            this.provincia       = provincia;
        }
    }

    /**
     * Consulta Nominatim para obtener el municipio de unas coordenadas.
     *
     * @param latitud  Latitud GPS del usuario
     * @param longitud Longitud GPS del usuario
     * @return ResultadoGeocodificacion con el código INE, o null si falla
     */
    public static ResultadoGeocodificacion obtenerMunicipio(
            double latitud, double longitud) {

        HttpURLConnection conexion = null;
        try {
            String urlStr = URL_NOMINATIM
                    + "&lat=" + latitud
                    + "&lon=" + longitud;
            
            Log.d(TAG, "Consultando Nominatim: " + urlStr);

            URL url = new URL(urlStr);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(8_000);
            conexion.setReadTimeout(8_000);
            // User-Agent obligatorio: Nominatim rechaza peticiones sin él
            conexion.setRequestProperty("User-Agent", USER_AGENT);
            conexion.connect();

            if (conexion.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error HTTP: " + conexion.getResponseCode());
                return null;
            }

            String respuesta = leerStream(conexion.getInputStream());
            return parsearRespuesta(respuesta);

        } catch (Exception e) {
            Log.e(TAG, "Error en geocodificación: " + e.getMessage());
            return null;
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    /**
     * Parsea la respuesta JSON de Nominatim y extrae el código INE.
     *
     * Nominatim devuelve un campo "address" con el desglose de la dirección.
     * El código INE lo incluye en "address.municipality" junto con otros campos.
     *
     * Ejemplo de respuesta relevante:
     * {
     *   "address": {
     *     "municipality": "Granada",
     *     "state": "Andalucía",
     *     "ISO3166-2-lvl4": "ES-AN",
     *     "country_code": "es"
     *   },
     *   "extratags": {
     *     "ref:INE": "18087"   ← aquí está el código INE
     *   }
     * }
     */
    private static ResultadoGeocodificacion parsearRespuesta(String json) {
        try {
            Log.d(TAG, "NOMINATIM JSON COMPLETO: " + json);
            JSONObject raiz = new JSONObject(json);

            // Intentar obtener el código INE de extratags
            String codigoIne = null;
            if (raiz.has("extratags")) {
                JSONObject extratags = raiz.getJSONObject("extratags");
                
                // Nominatim a veces usa "ref:INE", otras "ref:ine" (minúsculas)
                // y en Granada concretamente hemos visto "ine:municipio"
                if (extratags.has("ref:INE")) {
                    codigoIne = extratags.getString("ref:INE");
                } else if (extratags.has("ref:ine")) {
                    codigoIne = extratags.getString("ref:ine");
                } else if (extratags.has("ine:municipio")) {
                    codigoIne = extratags.getString("ine:municipio");
                }

                // El código INE en Nominatim puede venir con más dígitos (ej: 18087000000)
                // Los municipios españoles tienen exactamente 5 dígitos.
                if (codigoIne != null && codigoIne.length() > 5) {
                    codigoIne = codigoIne.substring(0, 5);
                }
            }

            // Obtener nombre del municipio y provincia del campo address
            String nombreMunicipio = "";
            String provincia       = "";

            if (raiz.has("address")) {
                JSONObject address = raiz.getJSONObject("address");

                // Nominatim usa diferentes campos según el tipo de lugar y la jerarquía.
                // Probamos en orden de prioridad para encontrar la entidad administrativa más relevante.
                if (address.has("municipality")) {
                    nombreMunicipio = address.getString("municipality");
                } else if (address.has("city")) {
                    nombreMunicipio = address.getString("city");
                } else if (address.has("town")) {
                    nombreMunicipio = address.getString("town");
                } else if (address.has("village")) {
                    nombreMunicipio = address.getString("village");
                } else if (address.has("city_district")) {
                    nombreMunicipio = address.getString("city_district");
                } else if (address.has("suburb")) {
                    nombreMunicipio = address.getString("suburb");
                }

                if (address.has("state")) {
                    provincia = address.getString("state");
                } else if (address.has("province")) {
                    provincia = address.getString("province");
                }
            }

            Log.d(TAG, "Municipio detectado: " + nombreMunicipio
                    + " (INE: " + codigoIne + ")");

            if (codigoIne != null && !codigoIne.isEmpty()) {
                return new ResultadoGeocodificacion(
                        codigoIne, nombreMunicipio, provincia);
            }

            // Si no hay INE pero tenemos nombre, devolvemos igualmente
            // para que el Repository pueda buscar por nombre como fallback
            if (!nombreMunicipio.isEmpty()) {
                return new ResultadoGeocodificacion(
                        null, nombreMunicipio, provincia);
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "Error parseando respuesta Nominatim: " + e.getMessage());
            return null;
        }
    }

    private static String leerStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder  sb     = new StringBuilder();
        String         linea;
        while ((linea = reader.readLine()) != null) {
            sb.append(linea);
        }
        reader.close();
        return sb.toString();
    }
}