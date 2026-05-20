package com.tupackage.radardenormativas.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.dao.MunicipioDao;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.data.remote.GeocodingService;

import java.util.List;

/**
 * Repository → Única fuente de verdad para datos de Municipio.
 *
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

    public LiveData<List<Municipio>> getTodos()
    {
        return municipioDao.obtenerTodos();
    }

    public LiveData<List<Municipio>> buscarPorNombre(String query)
    {
        return municipioDao.buscarPorNombre(query);
    }

    // ── Escritura (siempre en background) ────────────────────────────────────

    public void insertar(Municipio municipio)
    {
        RadarDatabase.databaseWriteExecutor.execute(
                () -> municipioDao.insertarMunicipio(municipio)
        );
    }

    public void actualizar(Municipio municipio)
    {
        RadarDatabase.databaseWriteExecutor.execute(
                () -> municipioDao.actualizarMunicipio(municipio)
        );
    }

    // ── Utilidad ──────────────────────────────────────────────────────────────

    /**
     * Comprueba en background si la BD tiene datos.
     * Lo usaremos para saber si hay que hacer una carga inicial.
     */
    public void hayDatos(java.util.function.Consumer<Boolean> callback)
    {
        RadarDatabase.databaseWriteExecutor.execute(() -> {
            boolean tiene = municipioDao.contarMunicipios() > 0;
            callback.accept(tiene);
        });
    }

    /**
     * Busca el municipio en el que está el usuario usando dos estrategias:
     *
     * 1. Geocodificación inversa (Nominatim) → exacta, requiere internet
     * 2. Distancia al centroide (BD local)   → aproximada, funciona offline
     *
     * IMPORTANTE: llamar siempre desde un hilo background.
     */
    public Municipio obtenerMunicipioActual(double latitud, double longitud)
    {

        //Utilizar Nominatim (si hay internet)
        GeocodingService.ResultadoGeocodificacion resultado =
                GeocodingService.obtenerMunicipio(latitud, longitud);

        if (resultado != null) {

            // Si obtenemos el INE → búsqueda exacta
            if (resultado.codigoIne != null) {
                Municipio municipio =
                        municipioDao.obtenerPorCodigoIneSync(resultado.codigoIne);
                if (municipio != null) {
                    Log.d("Repository", "Municipio por INE: "
                            + municipio.getNombre());
                    return municipio;
                }
            }

            // Obtenemos el nombre pero no el INE → búsqueda por nombre
            if (!resultado.nombreMunicipio.isEmpty()) {
                List<Municipio> porNombre =
                        municipioDao.buscarPorNombreSync(resultado.nombreMunicipio);
                if (porNombre != null && !porNombre.isEmpty()) {
                    Log.d("Repository", "Municipio por nombre: "
                            + porNombre.get(0).getNombre());
                    return porNombre.get(0);
                } else {
                    Log.w("Repository", "Nominatim devolvió '" + resultado.nombreMunicipio + 
                            "' pero no está en nuestra BD. Usando fallback por cercanía.");
                }
            }
        }

        // Si no hay internet o error de Nomination ----> fallback por distancia
        Log.d("Repository", "Calculando municipio más cercano por distancia a coordenadas: " + latitud + ", " + longitud);
        List<Municipio> cercanos =
                municipioDao.obtenerMunicipiosCercanosSync(latitud, longitud, 1);
        return (cercanos != null && !cercanos.isEmpty()) ? cercanos.get(0) : null;
    }

    // En MunicipioRepository.java, añade:

    /**
     * Combina detección precisa (Nominatim) con lista para Geofencing.
     * Devuelve lista donde el primer elemento es el municipio actual detectado.
     * IMPORTANTE: llamar desde background thread.
     */
    public List<Municipio> obtenerMunicipioActualConCercanos(double latitud, double longitud)
    {

        // Detectamos el municipio actual con precisión
        Municipio actual = obtenerMunicipioActual(latitud, longitud);

        // Obtenemos los cercanos para el Geofencing (radio amplio)
        List<Municipio> cercanos =
                municipioDao.obtenerMunicipiosCercanosSync(latitud, longitud, 50);

        if (actual != null && cercanos != null) {
            // El municipio detectado va primero aunque no sea el más cercano
            cercanos.remove(actual);
            cercanos.add(0, actual);
        }

        return cercanos != null ? cercanos : new java.util.ArrayList<>();
    }

}