package com.tupackage.radardenormativas.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.data.repository.MunicipioRepository;

import java.util.List;

public class SearchViewModel extends AndroidViewModel {

    private final MunicipioRepository repository;

    // El texto de búsqueda actual
    private final MutableLiveData<String> queryActual = new MutableLiveData<>("");

    // Los resultados observados por la UI
    private final LiveData<List<Municipio>> resultados;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        // Inicializar el repositorio primero
        repository = new MunicipioRepository(application);
        
        // Inicializar la transformación después de que el repository esté listo
        resultados = Transformations.switchMap(queryActual, query -> {
            if (query == null || query.trim().isEmpty()) {
                return repository.getTodos();
            } else {
                return repository.buscarPorNombre(query.trim());
            }
        });
    }

    public LiveData<List<Municipio>> getResultados() {
        return resultados;
    }

    /** La Activity llama a esto cada vez que el usuario escribe una letra */
    public void buscar(String query) {
        queryActual.setValue(query);
    }
}