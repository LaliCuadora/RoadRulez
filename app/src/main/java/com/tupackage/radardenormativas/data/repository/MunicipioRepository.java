package com.tupackage.radardenormativas.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.dao.MunicipioDao;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.data.remote.GeocodingService;
import com.tupackage.radardenormativas.data.remote.GeocodingService.ResultadoGeocodificacion;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository → Única fuente de verdad para datos de Municipio.
 *
 * Ahora mismo solo habla con Room (BD local).
 * En el futuro añadiremos aquí la llamada a la API REST para
 * sincronizar normativas sin tocar el ViewModel ni la UI.
 */
public class MunicipioRepository {

    private final MunicipioDao municipioDao;

    public MunicipioRepository(Application application) {
        RadarDatabase db = RadarDatabase.getInstance(application);
        municipioDao = db.municipioDao();
    }

    // ── Lectura ───────────────────────────────────────────────────────────────

    /**
     * Devuelve LiveData: Room actualiza la lista automáticamente
     * si los datos cambian en la BD. El ViewModel solo observa.
     */
    public LiveData<List<Municipio>> getMunicipiosCercanos(
            double latitud, double longitud, int limite) {
        return municipioDao.obtenerMunicipiosCercanos(latitud, longitud, limite);
    }

    public LiveData<List<Municipio>> getTodos() {
        return municipioDao.obtenerTodos();
    }

    public LiveData<List<Municipio>> buscarPorNombre(String query) {
        return municipioDao.buscarPorNombre(query);
    }

    /**
     * Devuelve una lista síncrona de los municipios más cercanos o detectados por Nominatim.
     * Úsalo dentro de background threads.
     */
    public List<Municipio> obtenerMunicipioActualConCercanos(double latitud, double longitud) {
        // 1. Intentar geocodificación inversa mediante Nominatim (API REST)
        ResultadoGeocodificacion resultado = GeocodingService.obtenerMunicipio(latitud, longitud);

        List<Municipio> cercanos = municipioDao.obtenerMunicipiosCercanosSync(latitud, longitud, 5);

        if (resultado != null && resultado.codigoIne != null) {
            Municipio detectado = municipioDao.obtenerPorCodigoIneSync(resultado.codigoIne);
            if (detectado != null) {
                List<Municipio> listaFinal = new ArrayList<>();
                listaFinal.add(detectado);
                for (Municipio m : cercanos) {
                    if (!m.getCodigoIne().equals(detectado.getCodigoIne())) {
                        listaFinal.add(m);
                    }
                }
                return listaFinal;
            }
        }

        // 2. Fallback: Si la llamada a Nominatim falla (sin conexión, timeout) o el INE no existe en BD,
        // devolvemos los municipios ordenados por proximidad calculada en SQLite/Room.
        return cercanos;
    }

    // ── Escritura (siempre en background) ────────────────────────────────────

    public void insertar(Municipio municipio) {
        RadarDatabase.databaseWriteExecutor.execute(
                () -> municipioDao.insertarMunicipio(municipio)
        );
    }

    public void actualizar(Municipio municipio) {
        RadarDatabase.databaseWriteExecutor.execute(
                () -> municipioDao.actualizarMunicipio(municipio)
        );
    }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    /**
     * Comprueba en background si la BD tiene datos.
     * Lo usaremos para saber si hay que hacer una carga inicial.
     */
    public void hayDatos(java.util.function.Consumer<Boolean> callback) {
        RadarDatabase.databaseWriteExecutor.execute(() -> {
            boolean tiene = municipioDao.contarMunicipios() > 0;
            callback.accept(tiene);
        });
    }

    /*
        Función Básica. Devuelve el conteo de el número de municipios que se encuentran en la base de datos
        (cuenta las líneas en la tabla Municipios).
     */
    public LiveData<Integer> getConteoMunicipiosCompletos(){
        return municipioDao.contarMunicipiosCompletos();
    }
}