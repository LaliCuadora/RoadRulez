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
    // ── Constructor: todo se inicializa aquí, en orden garantizado ────────────
    public MainViewModel(@NonNull Application application) {
        super(application);

        // 1. Primero el repository
        repository = new MunicipioRepository(application);

        // 2. Luego los LiveData simples
        ubicacionActual  = new MutableLiveData<>();
        estadoUbicacion  = new MutableLiveData<>(EstadoUbicacion.ESPERANDO_PERMISO);

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

    public void actualizarUbicacion(Location location) {
        ubicacionActual.setValue(location);
        estadoUbicacion.setValue(EstadoUbicacion.LISTO);
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