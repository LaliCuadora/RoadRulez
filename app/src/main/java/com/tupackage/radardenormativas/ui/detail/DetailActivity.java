package com.tupackage.radardenormativas.ui.detail;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tupackage.radardenormativas.R;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    // Clave para pasar el municipio entre Activities via Intent
    // La definimos como constante para evitar typos
    public static final String EXTRA_CODIGO_INE    = "extra_codigo_ine";
    public static final String EXTRA_NOMBRE        = "extra_nombre";
    public static final String EXTRA_PROVINCIA     = "extra_provincia";
    public static final String EXTRA_ESTADO        = "extra_estado";
    public static final String EXTRA_NORMATIVA     = "extra_normativa";
    public static final String EXTRA_FUENTE        = "extra_fuente";
    public static final String EXTRA_FECHA         = "extra_fecha";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // Activamos el botón "atrás" en la ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }

        // Recogemos los datos que nos pasó MainActivity
        String nombre    = getIntent().getStringExtra(EXTRA_NOMBRE);
        String provincia = getIntent().getStringExtra(EXTRA_PROVINCIA);
        String estado    = getIntent().getStringExtra(EXTRA_ESTADO);
        String normativa = getIntent().getStringExtra(EXTRA_NORMATIVA);
        String fuente    = getIntent().getStringExtra(EXTRA_FUENTE);
        long   fecha     = getIntent().getLongExtra(EXTRA_FECHA, 0);

        rellenarVistas(nombre, provincia, estado, normativa, fuente, fecha);
    }

    private void rellenarVistas(String nombre, String provincia, String estado,
                                String normativa, String fuente, long fecha) {

        // Textos básicos - Intercambiamos posiciones para que el Municipio
        // aparezca en el formato secundario y la Provincia en el principal
        ((TextView) findViewById(R.id.tv_detalle_municipio)).setText(provincia);
        ((TextView) findViewById(R.id.tv_detalle_provincia)).setText(nombre);
        ((TextView) findViewById(R.id.tv_detalle_normativa)).setText(normativa);

        // Fuente (vacío si no hay)
        TextView tvFuente = findViewById(R.id.tv_detalle_fuente);
        if (fuente != null && !fuente.isEmpty()) {
            tvFuente.setText(fuente);
        } else {
            tvFuente.setText("No disponible");
        }

        // Fecha formateada
        if (fecha > 0) {
            String fechaFormateada = new SimpleDateFormat(
                    "dd/MM/yyyy", Locale.getDefault()
            ).format(new Date(fecha));
            ((TextView) findViewById(R.id.tv_detalle_fecha))
                    .setText("Fecha de la normativa: " + fechaFormateada);
        }

        // Badge de estado con color de fondo dinámico
        TextView badge = findViewById(R.id.tv_detalle_badge);
        badge.setText(obtenerTextoEstado(estado));
        GradientDrawable bg = (GradientDrawable) badge.getBackground();
        bg.setColor(obtenerColor(estado));
    }

    private int obtenerColor(String estado) {
        if (estado == null) return getColor(R.color.estado_sin_datos);
        switch (estado) {
            case Municipio.EstadoNorma.PERMITIDO:   return getColor(R.color.estado_permitido);
            case Municipio.EstadoNorma.RESTRINGIDO: return getColor(R.color.estado_restringido);
            case Municipio.EstadoNorma.PROHIBIDO:   return getColor(R.color.estado_prohibido);
            default:                                return getColor(R.color.estado_sin_datos);
        }
    }

    private String obtenerTextoEstado(String estado) {
        if (estado == null) return "SIN DATOS";
        switch (estado) {
            case Municipio.EstadoNorma.PERMITIDO:   return "✅ PERMITIDO";
            case Municipio.EstadoNorma.RESTRINGIDO: return "⚠️ RESTRINGIDO";
            case Municipio.EstadoNorma.PROHIBIDO:   return "🚫 PROHIBIDO";
            default:                                return "❓ SIN DATOS";
        }
    }

    // El botón atrás de la ActionBar
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // cierra esta Activity y vuelve a MainActivity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}