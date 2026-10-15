package com.tupackage.radardenormativas.data.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.dao.MunicipioDao;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.geofencing.GeofenceManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * SyncManager → responsable de mantener la BD local actualizada.
 *
 * Dos operaciones principales:
 * 1. cargarDatosIniciales() → lee assets/normativas_v1.json e inserta en Room
 * 2. sincronizarSiHayNuevaVersion() → comprueba GitHub y actualiza si procede
 *
 * IMPORTANTE: todos los métodos públicos deben llamarse desde un hilo
 * background (el databaseWriteExecutor de RadarDatabase).
 * Ninguno toca el Main Thread directamente.
 */
public class SyncManager {

    private static final String TAG = "SyncManager";

    // Clave en SharedPreferences donde guardamos la versión instalada
    private static final String PREFS_NAME    = "radar_sync_prefs";
    private static final String KEY_VERSION   = "version_datos_local";
    private static final int    VERSION_NUEVA = 0; // 0 = nunca sincronizado

    // URL del JSON en GitHub (raw content, accesible sin autenticación)
    // Usamos la rama 'master' para obtener siempre el último contenido subido
    private static final String URL_JSON_REMOTO =
            "https://raw.githubusercontent.com/LaliCuadora/RoadRulez_Data/master/normativas_v1.json";
    private final Context      context;
    private final MunicipioDao dao;

    public SyncManager(Context context) {
        this.context          = context.getApplicationContext();
        this.dao              = RadarDatabase.getInstance(this.context).municipioDao();
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /**
     * Punto de entrada principal. Llama a esto al arrancar la app.
     * Decide automáticamente si hay que cargar datos iniciales
     * y si hay que intentar sincronizar con el servidor.
     */
    public void sincronizar() {
        int versionLocal = obtenerVersionLocal();

        // Primera vez: cargamos el JSON del APK
        if (versionLocal == VERSION_NUEVA || dao.contarMunicipios() == 0) {
            Log.d(TAG, "Primera ejecución: cargando datos del APK...");
            cargarDatosIniciales();
        }else {
            // Útil cuando publicas una nueva versión de la app con más municipios
            comprobarActualizacionLocal(versionLocal);
        }

        // Si hay internet, intentamos actualizar
        if (hayConexionInternet()) {
            Log.d(TAG, "Hay internet: comprobando actualizaciones...");
            sincronizarRemoto();
        } else {
            Log.d(TAG, "Sin internet: usando datos locales (v" + versionLocal + ")");
        }
    }

    // ── Carga inicial desde assets ────────────────────────────────────────────

    private void cargarDatosIniciales() {
        try {
            String json = leerAsset("normativas_v1.json");
            if (json == null) {
                Log.e(TAG, "No se encontró normativas_v1.json en assets");
                return;
            }

            procesarJson(json);
            Log.d(TAG, "Datos iniciales cargados correctamente");

        } catch (Exception e) {
            Log.e(TAG, "Error cargando datos iniciales: " + e.getMessage());
        }
    }

    // ── Sincronización remota ─────────────────────────────────────────────────

    private void sincronizarRemoto() {
        try {
            Log.d(TAG, "Iniciando descarga de JSON desde: " + URL_JSON_REMOTO);
            String jsonRemoto = descargarJson(URL_JSON_REMOTO);
            if (jsonRemoto == null) {
                Log.e(TAG, "Error: El JSON remoto está vacío o no se pudo descargar");
                return;
            }

            Log.d(TAG, "JSON descargado con éxito. Iniciando parseo...");
            NormativaJsonParser.ResultadoParseo resultado =
                    NormativaJsonParser.parsear(jsonRemoto);
            
            if (resultado == null) {
                Log.e(TAG, "Error: El parseo del JSON remoto falló");
                return;
            }

            int versionLocal  = obtenerVersionLocal();
            int versionRemota = resultado.version;

            Log.d(TAG, "Versión local: " + versionLocal + " | Versión remota: " + versionRemota);

            if (versionRemota > versionLocal) {
                Log.d(TAG, "Nueva versión disponible: " +
                        versionLocal + " → " + versionRemota + ". Insertando " + resultado.municipios.size() + " municipios");
                insertarMunicipios(resultado.municipios);
                guardarVersionLocal(versionRemota);
                
                Log.d(TAG, "Sincronización completada ✅ (v" + versionRemota + ")");
            } else {
                Log.d(TAG, "Los datos ya están actualizados (v" + versionLocal + ")");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error crítico en sincronización remota: " + e.getMessage(), e);
        }
    }

    // ── Utilidades de red ─────────────────────────────────────────────────────

    /**
     * Descarga el contenido de una URL como String.
     * Usa HttpURLConnection puro (sin librerías externas).
     * Timeout de 10s para no bloquear si el servidor tarda.
     */
    private String descargarJson(String urlString) {
        HttpURLConnection conexion = null;
        try {
            URL url = new URL(urlString);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(10_000); // 10 segundos
            conexion.setReadTimeout(10_000);
            conexion.connect();

            int codigoRespuesta = conexion.getResponseCode();
            if (codigoRespuesta != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error HTTP: " + codigoRespuesta);
                return null;
            }

            return leerStream(conexion.getInputStream());

        } catch (Exception e) {
            Log.e(TAG, "Error descargando JSON: " + e.getMessage());
            return null;
        } finally {
            if (conexion != null) conexion.disconnect();
        }
    }

    private boolean hayConexionInternet() {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }

    // ── Utilidades de archivo ─────────────────────────────────────────────────

    private String leerAsset(String nombreArchivo) {
        try {
            InputStream is = context.getAssets().open(nombreArchivo);
            return leerStream(is);
        } catch (Exception e) {
            Log.e(TAG, "Error leyendo asset " + nombreArchivo + ": " + e.getMessage());
            return null;
        }
    }

    private String leerStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder  sb     = new StringBuilder();
        String         linea;
        while ((linea = reader.readLine()) != null) {
            sb.append(linea);
        }
        reader.close();
        return sb.toString();
    }

    // ── Utilidades de BD ──────────────────────────────────────────────────────

    private void procesarJson(String json) {
        NormativaJsonParser.ResultadoParseo resultado =
                NormativaJsonParser.parsear(json);
        if (resultado == null) return;

        insertarMunicipios(resultado.municipios);
        guardarVersionLocal(resultado.version);
    }

    private void insertarMunicipios(List<Municipio> municipios) {
        // OnConflictStrategy.REPLACE en el DAO actualiza si ya existe
        dao.insertarMunicipios(municipios);
        Log.d(TAG, "Insertados/actualizados " + municipios.size() + " municipios");
    }

    // ── Gestión de versión ────────────────────────────────────────────────────

    private int obtenerVersionLocal() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_VERSION, VERSION_NUEVA);
    }

    private void guardarVersionLocal(int version) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_VERSION, version)
                .apply();
    }

    /**
     * Compara la versión del JSON en assets con la versión instalada en la BD.
     * Si el APK trae una versión más nueva, la carga.
     * Esto permite distribuir datos nuevos sin depender del servidor.
     */
    private void comprobarActualizacionLocal(int versionInstalada) {
        try {
            String json = leerAsset("normativas_v1.json");
            if (json == null) return;

            NormativaJsonParser.ResultadoParseo resultado =
                    NormativaJsonParser.parsear(json);
            if (resultado == null) return;

            if (resultado.version > versionInstalada) {
                Log.d(TAG, "JSON del APK más nuevo (" + resultado.version +
                        " > " + versionInstalada + "): actualizando...");
                insertarMunicipios(resultado.municipios);
                guardarVersionLocal(resultado.version);
            } else {
                Log.d(TAG, "JSON del APK ya instalado (v" + versionInstalada + ")");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error comparando versión local: " + e.getMessage());
        }
    }
}