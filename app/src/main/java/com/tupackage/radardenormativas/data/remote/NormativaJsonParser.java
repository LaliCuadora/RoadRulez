package com.tupackage.radardenormativas.data.remote;

import android.util.Log;

import com.tupackage.radardenormativas.data.local.entity.Municipio;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Parser puro: recibe un String JSON y devuelve una lista de Municipio.
 * No sabe de dónde viene el JSON (assets, red, fichero). Eso lo decide
 * quien lo llama. Así podemos reutilizarlo tanto para la carga inicial
 * como para las actualizaciones remotas.
 */
public class NormativaJsonParser {

    private static final String TAG = "NormativaJsonParser";

    /**
     * Resultado del parseo: incluye la versión del JSON además de los datos.
     * La versión nos sirve para saber si hay que actualizar la BD.
     */
    public static class ResultadoParseo {
        public final int            version;
        public final List<Municipio> municipios;

        public ResultadoParseo(int version, List<Municipio> municipios) {
            this.version    = version;
            this.municipios = municipios;
        }
    }

    /**
     * Parsea un String JSON y devuelve ResultadoParseo.
     * Devuelve null si el JSON está malformado.
     */
    public static ResultadoParseo parsear(String json) {
        try {
            JSONObject raiz      = new JSONObject(json);
            int        version   = raiz.getInt("version");
            JSONArray  arrayData = raiz.getJSONArray("municipios");

            List<Municipio> municipios = new ArrayList<>();

            for (int i = 0; i < arrayData.length(); i++) {
                JSONObject obj = arrayData.getJSONObject(i);

                // Convertimos la fecha "2024-03-15" a timestamp Unix
                long timestamp = parsearFecha(obj.getString("ultima_actualizacion"));

                Municipio municipio = new Municipio(
                        obj.getString("codigo_ine"),
                        obj.getString("nombre"),
                        obj.getString("provincia"),
                        obj.getDouble("latitud"),
                        obj.getDouble("longitud"),
                        obj.getString("estado_norma"),
                        obj.getString("texto_normativa"),
                        obj.getString("resumen_corto"),
                        timestamp,
                        obj.optString("fuente_url", "") // optString: no falla si falta
                );

                municipios.add(municipio);
            }

            Log.d(TAG, "Parseados " + municipios.size() +
                    " municipios (versión " + version + ")");
            return new ResultadoParseo(version, municipios);

        } catch (Exception e) {
            Log.e(TAG, "Error parseando JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convierte "dd/MM/yyyy" → milisegundos Unix.
     * Devuelve System.currentTimeMillis() si falla el parseo.
     */
    private static long parsearFecha(String fechaStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date date = sdf.parse(fechaStr);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}