package com.tupackage.radardenormativas.ui.main;

import android.app.Application;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.data.repository.MunicipioRepository;

import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final MunicipioRepository repository;
    private final MutableLiveData<Location> ubicacionActual;
    private final MutableLiveData<EstadoUbicacion> estadoUbicacion;
    private final LiveData<Double> porcentajeCompletado;

    // ── Ahora es solo una declaración, sin inicializar aquí ──────────────────
    public final LiveData<List<Municipio>> municipiosCercanos;

    public enum EstadoUbicacion {
        ESPERANDO_PERMISO,
        OBTENIENDO_GPS,
        LISTO,
        ERROR_PERMISO,
        ERROR_GPS
    }

    // ── Constructor: todo se inicializa aquí, en orden garantizado ────────────
    public MainViewModel(@NonNull Application application) {
        super(application);

        // 1. Primero el repository
        repository = new MunicipioRepository(application);

        // 2. Luego los LiveData simples
        ubicacionActual  = new MutableLiveData<>();
        estadoUbicacion  = new MutableLiveData<>(EstadoUbicacion.ESPERANDO_PERMISO);
        porcentajeCompletado = Transformations.map(repository.getConteoMunicipiosCompletos(),
                                            count ->
                                                {
                                                    if (count == null) return 0.0;
                                                    return count * 100.0 / 8131.0;
                                                });

        // 3. Por último el switchMap, integrando tu nueva lógica con el executor
        municipiosCercanos = Transformations.switchMap(ubicacionActual, location -> {
            if (location == null) return new MutableLiveData<>(null);

            MutableLiveData<List<Municipio>> resultado = new MutableLiveData<>();

            RadarDatabase.databaseWriteExecutor.execute(() -> {
                List<Municipio> municipios =
                        repository.obtenerMunicipioActualConCercanos(
                                location.getLatitude(),
                                location.getLongitude()
                        );
                // Usamos postValue porque estamos en un hilo de fondo (executor)
                resultado.postValue(municipios);
            });

            return resultado;
        });
    }

    public LiveData<Double> getPorcentajeCompletado() {
        return porcentajeCompletado;
    }

    public void actualizarUbicacion(Location location) {
        if (location == null) return;
        
        // Solo actualizamos si la ubicación es distinta o no teníamos ninguna
        Location actual = ubicacionActual.getValue();
        if (actual == null || 
            actual.getLatitude() != location.getLatitude() || 
            actual.getLongitude() != location.getLongitude()) {
            
            ubicacionActual.setValue(location);
        }
        
        // Siempre marcamos como LISTO si recibimos una ubicación válida
        if (estadoUbicacion.getValue() != EstadoUbicacion.LISTO) {
            estadoUbicacion.setValue(EstadoUbicacion.LISTO);
        }
    }

    public void reportarErrorPermiso() {
        estadoUbicacion.setValue(EstadoUbicacion.ERROR_PERMISO);
    }

    public void iniciarBusquedaGps() {
        estadoUbicacion.setValue(EstadoUbicacion.OBTENIENDO_GPS);
    }

    public LiveData<EstadoUbicacion> getEstadoUbicacion() {
        return estadoUbicacion;
    }

    public LiveData<Location> getUbicacionActual() {
        return ubicacionActual;
    }
}