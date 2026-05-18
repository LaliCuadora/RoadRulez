package com.tupackage.radardenormativas.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * ENTIDAD ROOM → Se convierte en la tabla "municipios" en SQLite.
 *
 * ¿Por qué esta clase y no un simple HashMap?
 * Room mapea cada instancia de esta clase a una fila de la tabla.
 * El compilador de Room lee las anotaciones (@Entity, @PrimaryKey, etc.)
 * y genera todo el SQL necesario ANTES de que la app se ejecute.
 */
@Entity(tableName = "municipios") // <- Nombre de la tabla en SQLite
public class Municipio {

    // ── CLAVE PRIMARIA ────────────────────────────────────────────────────────
    // Usamos el código INE (Instituto Nacional de Estadística) como ID único.
    // Ejemplo: "18087" para Granada. Es más fiable que un autoincremental
    // porque es el identificador oficial español para cada municipio.
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "codigo_ine")
    private String codigoIne;

    // ── DATOS DE IDENTIFICACIÓN ───────────────────────────────────────────────
    @NonNull
    @ColumnInfo(name = "nombre")
    private String nombre;          // "Granada", "Marbella", "Ronda"

    @ColumnInfo(name = "provincia")
    private String provincia;       // "Andalucía", "Cataluña"...

    // ── COORDENADAS DEL CENTRO DEL MUNICIPIO ─────────────────────────────────
    // Guardamos el centroide para calcular distancias desde el GPS del usuario.
    // No son los límites exactos (para eso usaremos Geofencing más adelante).
    @ColumnInfo(name = "latitud")
    private double latitud;         // Ejemplo: 37.1773

    @ColumnInfo(name = "longitud")
    private double longitud;        // Ejemplo: -3.5986

    // ── NORMATIVA DE PERNOCTA ─────────────────────────────────────────────────
    /**
     * EstadoNorma: usamos un String en lugar de un enum de Java para que
     * Room lo almacene de forma legible en la BD. Los valores posibles son
     * las constantes definidas abajo en la clase estática EstadoNorma.
     *
     * Valores:   PERMITIDO | RESTRINGIDO | PROHIBIDO | SIN_DATOS
     */
    @NonNull
    @ColumnInfo(name = "estado_norma")
    private String estadoNorma;

    /**
     * Texto completo de la normativa. Puede ser largo (párrafos de ordenanza
     * municipal). Room lo guarda como TEXT en SQLite sin límite de tamaño.
     *
     * Ejemplo: "Según la Ordenanza Municipal de Convivencia, art. 34,
     * queda prohibido pernoctar en vehículos a menos de 500m de la costa..."
     */
    @ColumnInfo(name = "texto_normativa")
    private String textoNormativa;

    /**
     * Resumen corto para mostrar en la notificación/tarjeta principal.
     * Máx ~150 caracteres. Este es el texto que verá el usuario de un vistazo.
     */
    @ColumnInfo(name = "resumen_corto")
    private String resumenCorto;

    // ── METADATOS ─────────────────────────────────────────────────────────────
    /**
     * Guardamos la fecha como timestamp Unix (milisegundos desde 1970).
     * Es el formato más eficiente para almacenar y comparar fechas en SQLite.
     * Para mostrarlo al usuario lo convertimos con SimpleDateFormat.
     */
    @ColumnInfo(name = "ultima_actualizacion")
    private long ultimaActualizacion;  // System.currentTimeMillis()

    @ColumnInfo(name = "fuente_url")
    private String fuenteUrl;          // URL del BOE/BOP donde está la norma


    // ── CLASE INTERNA: CONSTANTES DE ESTADO ──────────────────────────────────
    /**
     * Equivale a un enum pero compatible 100% con Room sin TypeConverters.
     * Úsalo así en tu código: Municipio.EstadoNorma.PROHIBIDO
     */
    public static class EstadoNorma {
        public static final String PERMITIDO    = "PERMITIDO";    // Verde 🟢
        public static final String RESTRINGIDO  = "RESTRINGIDO";  // Amarillo 🟡
        public static final String PROHIBIDO    = "PROHIBIDO";    // Rojo 🔴
        public static final String SIN_DATOS    = "SIN_DATOS";    // Gris ⚫
    }


    // ── CONSTRUCTOR COMPLETO ──────────────────────────────────────────────────
    // Room NECESITA un constructor público. Puede ser vacío o con parámetros.
    // Este constructor nos facilita crear objetos completos en una línea.
    public Municipio(@NonNull String codigoIne,
                     @NonNull String nombre,
                     String provincia,
                     double latitud,
                     double longitud,
                     @NonNull String estadoNorma,
                     String textoNormativa,
                     String resumenCorto,
                     long ultimaActualizacion,
                     String fuenteUrl) {

        this.codigoIne          = codigoIne;
        this.nombre             = nombre;
        this.provincia          = provincia;
        this.latitud            = latitud;
        this.longitud           = longitud;
        this.estadoNorma        = estadoNorma;
        this.textoNormativa     = textoNormativa;
        this.resumenCorto       = resumenCorto;
        this.ultimaActualizacion = ultimaActualizacion;
        this.fuenteUrl          = fuenteUrl;
    }

    // ── GETTERS Y SETTERS ─────────────────────────────────────────────────────
    // Room usa los getters para leer los datos al guardarlos en la BD.
    // Android Studio puede generarlos: clic derecho → Generate → Getter and Setter

    @NonNull public String getCodigoIne()           { return codigoIne; }
    public void setCodigoIne(@NonNull String v)     { this.codigoIne = v; }

    @NonNull public String getNombre()              { return nombre; }
    public void setNombre(@NonNull String v)        { this.nombre = v; }

    public String getProvincia()                    { return provincia; }
    public void setProvincia(String v)              { this.provincia = v; }

    public double getLatitud()                      { return latitud; }
    public void setLatitud(double v)                { this.latitud = v; }

    public double getLongitud()                     { return longitud; }
    public void setLongitud(double v)               { this.longitud = v; }

    @NonNull public String getEstadoNorma()         { return estadoNorma; }
    public void setEstadoNorma(@NonNull String v)   { this.estadoNorma = v; }

    public String getTextoNormativa()               { return textoNormativa; }
    public void setTextoNormativa(String v)         { this.textoNormativa = v; }

    public String getResumenCorto()                 { return resumenCorto; }
    public void setResumenCorto(String v)           { this.resumenCorto = v; }

    public long getUltimaActualizacion()            { return ultimaActualizacion; }
    public void setUltimaActualizacion(long v)      { this.ultimaActualizacion = v; }

    public String getFuenteUrl()                    { return fuenteUrl; }
    public void setFuenteUrl(String v)              { this.fuenteUrl = v; }
}