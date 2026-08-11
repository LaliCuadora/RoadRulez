package com.tupackage.radardenormativas.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.tupackage.radardenormativas.data.local.entity.Municipio;

import java.util.List;

/**
 * DAO → El "traductor" entre tu código Java y la base de datos SQLite.
 *
 * Reglas importantes:
 * 1. Siempre es una @interface (interfaz), nunca una clase.
 * 2. NUNCA ejecutes métodos de DAO en el hilo principal (Main Thread).
 *    Android lo bloqueará y lanzará una excepción. Usaremos Executors
 *    o LiveData (que ya es asíncrono) para evitarlo.
 * 3. Los métodos que devuelven LiveData<> son AUTOMÁTICAMENTE asíncronos
 *    y se actualizan solos cuando cambian los datos en la BD. ✨
 */
@Dao
public interface MunicipioDao {

    // ── ESCRITURA ─────────────────────────────────────────────────────────────

    /**
     * Inserta UN municipio. Si ya existe (mismo codigoIne), lo REEMPLAZA.
     * OnConflictStrategy.REPLACE = útil para sincronizar datos desde la API.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarMunicipio(Municipio municipio);

    /**
     * Inserta una lista entera. Perfecto para la carga inicial de datos
     * o para sincronizaciones en bloque desde el servidor.
     * Devuelve los rowIds de las filas insertadas (útil para debug).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertarMunicipios(List<Municipio> municipios);

    /**
     * Actualiza un municipio existente (busca por PrimaryKey automáticamente).
     * Úsalo cuando solo cambia la normativa de un municipio ya en la BD.
     */
    @Update
    void actualizarMunicipio(Municipio municipio);


    // ── LECTURA ───────────────────────────────────────────────────────────────

    /**
     * Busca un municipio por su código INE exacto.
     * Devuelve LiveData<> → la UI se actualizará automáticamente si cambia.
     * Devuelve null si no existe en la BD.
     */
    @Query("SELECT * FROM municipios WHERE codigo_ine = :codigoIne LIMIT 1")
    LiveData<Municipio> obtenerPorCodigoIne(String codigoIne);

    /**
     * Versión SÍNCRONA de la búsqueda por código INE.
     * Úsala cuando necesitas el resultado inmediatamente dentro de un
     * background thread (Executor). NO en el Main Thread.
     */
    @Query("SELECT * FROM municipios WHERE codigo_ine = :codigoIne LIMIT 1")
    Municipio obtenerPorCodigoIneSync(String codigoIne);

    /**
     * ★ CONSULTA DE PROXIMIDAD ★
     *
     * Calcula la distancia aproximada usando la fórmula de distancia
     * euclidiana en coordenadas (válida para distancias cortas < 50km).
     *
     * ¿Por qué no la fórmula de Haversine?
     * SQLite no tiene funciones trigonométricas (sin, cos, acos).
     * Para distancias cortas, esta aproximación es suficientemente
     * precisa y mucho más rápida. La Haversine la aplicaremos en Java
     * si necesitamos mayor precisión en el futuro.
     *
     * Filtramos primero por un "bounding box" (±0.5 grados ≈ 50km)
     * para reducir el número de filas antes de ordenar. Luego
     * ordenamos por distancia euclidiana ascendente.
     *
     * @param latitud  Latitud GPS actual del usuario
     * @param longitud Longitud GPS actual del usuario
     * @param limite   Cuántos municipios cercanos devolver (normalmente 1 o 5)
     */
    @Query("SELECT * FROM municipios " +
            "WHERE latitud  BETWEEN :latitud  - 0.5 AND :latitud  + 0.5 " +
            "  AND longitud BETWEEN :longitud - 0.5 AND :longitud + 0.5 " +
            "ORDER BY " +
            "  ((:latitud  - latitud)  * (:latitud  - latitud) + " +
            "   (:longitud - longitud) * (:longitud - longitud)) ASC " +
            "LIMIT :limite")
    LiveData<List<Municipio>> obtenerMunicipiosCercanos(
            double latitud, double longitud, int limite);

    /**
     * Versión síncrona de obtenerMunicipiosCercanos.
     * El Repository la usará desde un Executor para el Geofencing.
     */
    @Query("SELECT * FROM municipios " +
            "WHERE latitud  BETWEEN :latitud  - 0.5 AND :latitud  + 0.5 " +
            "  AND longitud BETWEEN :longitud - 0.5 AND :longitud + 0.5 " +
            "ORDER BY " +
            "  ((:latitud  - latitud)  * (:latitud  - latitud) + " +
            "   (:longitud - longitud) * (:longitud - longitud)) ASC " +
            "LIMIT :limite")
    List<Municipio> obtenerMunicipiosCercanosSync(
            double latitud, double longitud, int limite);

    /**
     * Todos los municipios. Útil para poblar un mapa con marcadores
     * o para la pantalla de "explorar normativas".
     */
    @Query("SELECT * FROM municipios ORDER BY nombre ASC")
    LiveData<List<Municipio>> obtenerTodos();

    /**
     * Versión síncrona de obtenerTodos.
     * La usaremos para registrar todos los geofences al arrancar la app.
     */
    @Query("SELECT * FROM municipios")
    List<Municipio> obtenerTodosSync();

    /**
     * Busca municipios por nombre (búsqueda parcial, insensible a mayúsculas).
     * El '%' en SQL significa "cualquier secuencia de caracteres".
     * Ejemplo: buscarPorNombre("gran") → devuelve "Granada", "La Granja"...
     */
    @Query("SELECT * FROM municipios WHERE nombre LIKE '%' || :query || '%' " +
            "ORDER BY nombre ASC")
    LiveData<List<Municipio>> buscarPorNombre(String query);

    /**
     * Cuenta el total de municipios. Útil para saber si la BD está vacía
     * y necesita carga inicial de datos.
     */
    @Query("SELECT COUNT(*) FROM municipios")
    int contarMunicipios();

    /**
     * Cuenta cuántos municipios tienen ya una normativa asignada.
     * Consideramos "completo" cualquier municipio que NO esté en estado SIN_DATOS.
     */
    @Query("SELECT COUNT(*) FROM municipios WHERE estado_norma != 'SIN_DATOS'")
    int contarMunicipiosCompletos();
}