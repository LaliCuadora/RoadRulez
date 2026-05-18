package com.tupackage.radardenormativas.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView;

import com.tupackage.radardenormativas.R;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.ui.detail.DetailActivity;

public class SearchActivity extends AppCompatActivity
        implements MunicipioAdapter.OnMunicipioClickListener {

    private SearchViewModel  viewModel;
    private MunicipioAdapter adapter;
    private TextView         tvContador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        configurarActionBar();
        configurarRecyclerView();
        configurarSearchView();
        observarResultados();
    }

    private void configurarActionBar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Explorar municipios");
        }
    }

    private void configurarRecyclerView() {
        adapter   = new MunicipioAdapter(this);
        tvContador = findViewById(R.id.tv_contador);

        RecyclerView recycler = findViewById(R.id.recycler_municipios);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        // Mejora el rendimiento si el tamaño del RecyclerView no cambia
        recycler.setHasFixedSize(false);
    }

    private void configurarSearchView() {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        SearchView searchView = findViewById(R.id.search_view);
        searchView.setQueryHint("Nombre del municipio...");
        searchView.setIconifiedByDefault(false); // siempre expandido

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.buscar(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // Busca en tiempo real mientras escribe
                viewModel.buscar(newText);
                return true;
            }
        });
    }

    private void observarResultados() {
        viewModel.getResultados().observe(this, municipios -> {
            adapter.submitList(municipios);

            // Actualizamos el contador
            if (municipios == null || municipios.isEmpty()) {
                tvContador.setText("Sin resultados");
            } else {
                tvContador.setText(municipios.size() + " municipios");
            }
        });
    }

    // Clic en un municipio → abre el detalle
    @Override
    public void onMunicipioClick(Municipio municipio) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_NOMBRE,    municipio.getNombre());
        intent.putExtra(DetailActivity.EXTRA_PROVINCIA, municipio.getProvincia());
        intent.putExtra(DetailActivity.EXTRA_ESTADO,    municipio.getEstadoNorma());
        intent.putExtra(DetailActivity.EXTRA_NORMATIVA, municipio.getTextoNormativa());
        intent.putExtra(DetailActivity.EXTRA_FUENTE,    municipio.getFuenteUrl());
        intent.putExtra(DetailActivity.EXTRA_FECHA,     municipio.getUltimaActualizacion());
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}