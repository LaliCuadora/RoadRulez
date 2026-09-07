package com.tupackage.radardenormativas.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.dao.MunicipioDao;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

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
     * Devuelve una lista síncrona de los municipios más cercanos.
     * Úsalo dentro de background threads.
     */
    public List<Municipio> obtenerMunicipioActualConCercanos(double latitud, double longitud) {
        return municipioDao.obtenerMunicipiosCercanosSync(latitud, longitud, 5);
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