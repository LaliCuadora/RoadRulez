package com.tupackage.radardenormativas;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.geofencing.GeofenceManager;
import com.tupackage.radardenormativas.ui.main.MainViewModel;
import com.tupackage.radardenormativas.ui.notifications.NotificationHelper;
import android.content.Intent;
import com.tupackage.radardenormativas.ui.detail.DetailActivity;
import com.tupackage.radardenormativas.ui.search.SearchActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ── Campos ────────────────────────────────────────────────────────────────
    private MainViewModel            viewModel;
    private FusedLocationProviderClient fusedLocationClient;
    private GeofenceManager          geofenceManager;
    private LocationCallback         locationCallback;
    private LocationRequest          locationRequest;
    private boolean                  geofencesRegistrados = false;

    private static final String PREF_PRIMERA_VEZ = "es_primera_vez";

    private Municipio municipioActual; // campo nuevo para guardar referencia

    // ── Vistas ────────────────────────────────────────────────────────────────
    private TextView tvEstado;
    private TextView tvMunicipio;
    private TextView tvNorma;
    private View     indicadorColor;

    private TextView tvProvincia;
    private TextView tvBadgeEstado;
    private TextView tvFecha;
    private TextView tvPorcentaje;
    private View     tvIconoUbicacion;
    private com.google.android.material.button.MaterialButton btnVerCompleta;
    private androidx.cardview.widget.CardView cardNormativa;

    // ── Launchers de permisos ─────────────────────────────────────────────────

    private final ActivityResultLauncher<String> pedirPermisoBackground =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    concedido -> {
                        if (concedido) {
                            // Si nos dan el permiso de background, registramos los geofences
                            RadarDatabase.databaseWriteExecutor.execute(() -> {
                                new com.tupackage.radardenormativas.data.remote.SyncManager(this).refrescarGeofences();
                            });
                        } else {
                            mostrarDialogoPermisoBackground();
                        }
                    }
            );

    private final ActivityResultLauncher<String[]> pedirPermisosIniciales =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    permisos -> {
                        boolean finoConcedido = Boolean.TRUE.equals(
                                permisos.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean groseoConcedido = Boolean.TRUE.equals(
                                permisos.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                        
                        // El de notificaciones es opcional para el core, pero mejor tenerlo
                        boolean notifConcedido = true;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifConcedido = Boolean.TRUE.equals(
                                    permisos.get(Manifest.permission.POST_NOTIFICATIONS));
                        }

                        if (finoConcedido || groseoConcedido) {
                            viewModel.iniciarBusquedaGps();
                            obtenerUbicacion();
                            pedirPermisoBackgroundSiNecesario();
                        } else {
                            viewModel.reportarErrorPermiso();
                        }
                    }
            );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inicializarVistas();
        inicializarViewModel();
        inicializarGps();
        NotificationHelper.crearCanalNotificaciones(this);
        geofenceManager = new GeofenceManager(this);
        observarEstados();
        comprobarYPedirPermisos();
        
        SharedPreferences prefs = getSharedPreferences("RoadRulezPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("descargo_aceptado", false)) {
            mostrarDescargoResponsabilidad(prefs);
        }    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationCallback != null) {
            boolean tienePermiso = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED;

            if (tienePermiso) {
                //noinspection MissingPermission
                fusedLocationClient.requestLocationUpdates(
                        locationRequest, locationCallback, getMainLooper()
                );
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    //Crea el menu de aplicaciones
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    /// FUNCIONES DE MENU
    ///
    ///////////////////////////////////////////////////////////////////////////

    private void mostrarDialogoTema() {
        String[] opciones = {"🌙 Oscuro", "☀️ Claro", "⚙️ Seguir sistema"};
        int[] valores = {
                RadarApplication.TEMA_OSCURO,
                RadarApplication.TEMA_CLARO,
                RadarApplication.TEMA_SISTEMA
        };

        // Marcamos el tema activo
        int actual = AppCompatDelegate.getDefaultNightMode();
        int seleccionado = 0;
        for (int i = 0; i < valores.length; i++) {
            if (valores[i] == actual) { seleccionado = i; break; }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tema de la aplicación")
                .setSingleChoiceItems(opciones, seleccionado, (dialog, which) -> {
                    RadarApplication.cambiarTema(this, valores[which]);
                    dialog.dismiss();
                })
                .show();
    }

    /*
        MUestra por pantalla la información sobre la información de que significa cada color.
         Muestra el archivo xml dialog_ayuda.
     */
    private void mostrarDialogoAyuda() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                //Seleccionar el layout de dialog_ayuda
                .setView(R.layout.dialog_ayuda)
                //Añadir boton de cierre
                .setPositiveButton("Entendido", null)
                // .show() es la orden final para que aparezca en pantalla
                .show();
    }

        @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_tema) {
            mostrarDialogoTema();
            return true;
        }
        // ← NUEVO
        if (item.getItemId() == R.id.action_buscar) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_info) {
            mostrarDialogoAyuda();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Inicialización ────────────────────────────────────────────────────────

    private void inicializarVistas() {
        tvEstado        = findViewById(R.id.tv_estado);
        tvMunicipio     = findViewById(R.id.tv_municipio);
        tvNorma         = findViewById(R.id.tv_norma);
        tvProvincia     = findViewById(R.id.tv_provincia);
        tvBadgeEstado   = findViewById(R.id.tv_badge_estado);
        tvFecha         = findViewById(R.id.tv_fecha);
        tvPorcentaje    = findViewById(R.id.tv_porcentaje_datos);
        tvIconoUbicacion = findViewById(R.id.tv_icono_ubicacion);
        indicadorColor  = findViewById(R.id.view_indicador_color);
        cardNormativa   = findViewById(R.id.card_normativa);
        btnVerCompleta  = findViewById(R.id.btn_ver_completa);
    }

    private void inicializarViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
    }

    private void inicializarGps() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    // ── Observadores ─────────────────────────────────────────────────────────

    private void observarEstados() {

        viewModel.getEstadoUbicacion().observe(this, estado -> {
            switch (estado) {
                case ESPERANDO_PERMISO:
                    tvEstado.setText("Solicitando permiso de ubicación...");
                    break;
                case OBTENIENDO_GPS:
                    tvEstado.setText("Obteniendo tu posición GPS...");
                    break;
                case LISTO:
                    tvEstado.setVisibility(View.GONE);
                    break;
                case ERROR_PERMISO:
                    tvEstado.setText("⚠️ Permiso de ubicación denegado.\n" +
                            "Ve a Ajustes → Permisos para activarlo.");
                    break;
                case ERROR_GPS:
                    tvEstado.setText("⚠️ No se pudo obtener la ubicación.\n" +
                            "Comprueba que el GPS está activado.");
                    break;
            }
        });

        viewModel.municipiosCercanos.observe(this, municipios -> {
            if (municipios == null || municipios.isEmpty()) return;

            mostrarInfoMunicipio(municipios.get(0));
        });

        viewModel.getPorcentajeCompletado().observe(this, porcentaje -> {
            if (tvPorcentaje != null) {
                tvPorcentaje.setText(String.format(Locale.getDefault(),
                        "Porcentaje de municipios con datos: %.1f%%", porcentaje));
            }
        });
    }

    // ── Permisos ──────────────────────────────────────────────────────────────

    private void comprobarYPedirPermisos() {
        boolean tieneFino = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean tieneNotif = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tieneNotif = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }

        if (tieneFino && tieneNotif) {
            viewModel.iniciarBusquedaGps();
            obtenerUbicacion();
            pedirPermisoBackgroundSiNecesario();
        } else {
            java.util.List<String> lista = new java.util.ArrayList<>();
            lista.add(Manifest.permission.ACCESS_FINE_LOCATION);
            lista.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lista.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            pedirPermisosIniciales.launch(lista.toArray(new String[0]));
        }
    }

    private void mostrarDescargoResponsabilidad(SharedPreferences prefs)
    {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(R.layout.dialog_descargo_responsabilidad)
                .setCancelable(false) // Obliga al usuario a pulsar el botón
                .setPositiveButton("Aceptar y Continuar", (dialog, which) -> {
                    // Guardamos que ya lo ha visto para que no vuelva a salir
                    prefs.edit().putBoolean("descargo_aceptado", true).apply();
                })
                .show();
    }

    private void pedirPermisoBackgroundSiNecesario() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // En versiones viejas el permiso de background se concede con el normal,
            // así que ya podemos registrar geofences.
            RadarDatabase.databaseWriteExecutor.execute(() -> {
                new com.tupackage.radardenormativas.data.remote.SyncManager(this).refrescarGeofences();
            });
            return;
        }

        boolean yaTiene = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        if (yaTiene) {
            // Si ya lo tiene, nos aseguramos de que los geofences estén activos
            RadarDatabase.databaseWriteExecutor.execute(() -> {
                new com.tupackage.radardenormativas.data.remote.SyncManager(this).refrescarGeofences();
            });
        } else {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Permiso de ubicación en segundo plano")
                    .setMessage(
                            "Para avisarte al entrar en un municipio aunque la app " +
                                    "esté cerrada, necesitamos acceso a tu ubicación en todo momento.\n\n" +
                                    "En la siguiente pantalla selecciona \"Permitir siempre\"."
                    )
                    .setPositiveButton("Entendido", (dialog, which) ->
                            pedirPermisoBackground.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                    )
                    .setNegativeButton("Ahora no", null)
                    .show();
        }
    }

    private void mostrarDialogoPermisoBackground() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Alertas desactivadas")
                .setMessage(
                        "Sin el permiso de ubicación en segundo plano no podemos " +
                                "enviarte alertas al entrar en un municipio.\n\n" +
                                "Puedes activarlo en Ajustes → Aplicaciones → " +
                                "Radar de Normativas → Permisos → Ubicación → Permitir siempre."
                )
                .setPositiveButton("Ir a Ajustes", (dialog, which) -> {
                    Intent intent = new Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    );
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    @SuppressWarnings("MissingPermission")
    private void obtenerUbicacion() {
        // Primero intentamos obtener la ÚLTIMA ubicación conocida para no esperar al GPS si ya existe una
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                viewModel.actualizarUbicacion(location);
            }
        });

        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000L) //Intervalo de esperar para consultar ubicación
                .setMinUpdateIntervalMillis(2000L)                 //Intervalo de espera mínimo para consultar ubicación
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                
                // Intentamos obtener la última ubicación conocida de la lista
                Location location = result.getLastLocation();
                
                // Si getLastLocation() es nulo pero hay localizaciones en el resultado, tomamos la última
                if (location == null && !result.getLocations().isEmpty()) {
                    location = result.getLocations().get(result.getLocations().size() - 1);
                }

                if (location != null) {
                    viewModel.actualizarUbicacion(location);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, getMainLooper()
        );
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void mostrarInfoMunicipio(Municipio municipio) {
        if (municipio == null) return;
        this.municipioActual = municipio;

        // Aseguramos que todas las vistas necesarias sean visibles
        if (tvIconoUbicacion != null) tvIconoUbicacion.setVisibility(View.VISIBLE);
        if (tvMunicipio != null) tvMunicipio.setVisibility(View.VISIBLE);
        if (tvProvincia != null) tvProvincia.setVisibility(View.VISIBLE);
        if (cardNormativa != null) cardNormativa.setVisibility(View.VISIBLE);

        // Ocultamos el mensaje de "Obteniendo ubicación"
        if (tvEstado != null) tvEstado.setVisibility(View.GONE);

        // Rellenamos datos
        if (tvMunicipio != null) tvMunicipio.setText(municipio.getNombre());
        if (tvProvincia != null) tvProvincia.setText(municipio.getProvincia());
        if (tvNorma != null) tvNorma.setText(municipio.getResumenCorto());

        // Badge de estado
        String textoEstado = obtenerTextoEstado(municipio.getEstadoNorma());
        if (tvBadgeEstado != null) {
            tvBadgeEstado.setText(textoEstado);
        }

        // Fecha
        if (municipio.getUltimaActualizacion() > 0) {
            String fecha = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(new Date(municipio.getUltimaActualizacion()));
            tvFecha.setText("Fecha de la normativa: " + fecha);
        }

        // Color del indicador y badge
        int color = obtenerColor(municipio.getEstadoNorma());
        indicadorColor.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        
        // El background de tvBadgeEstado podría ser nulo si no se ha inflado correctamente o no tiene el drawable
        if (tvBadgeEstado.getBackground() instanceof GradientDrawable) {
            GradientDrawable bg = (GradientDrawable) tvBadgeEstado.getBackground();
            bg.setColor(color);
        }

        // Navegación al detalle
        btnVerCompleta.setOnClickListener(v -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra(DetailActivity.EXTRA_NOMBRE,    municipio.getNombre());
            intent.putExtra(DetailActivity.EXTRA_PROVINCIA, municipio.getProvincia());
            intent.putExtra(DetailActivity.EXTRA_ESTADO,    municipio.getEstadoNorma());
            intent.putExtra(DetailActivity.EXTRA_NORMATIVA, municipio.getTextoNormativa());
            intent.putExtra(DetailActivity.EXTRA_FUENTE,    municipio.getFuenteUrl());
            intent.putExtra(DetailActivity.EXTRA_FECHA,     municipio.getUltimaActualizacion());
            startActivity(intent);
        });
    }

    private int obtenerColor(String estado) {
        switch (estado) {
            case Municipio.EstadoNorma.PERMITIDO:   return getColor(R.color.estado_permitido);
            case Municipio.EstadoNorma.RESTRINGIDO: return getColor(R.color.estado_restringido);
            case Municipio.EstadoNorma.PROHIBIDO:   return getColor(R.color.estado_prohibido);
            default:                                return getColor(R.color.estado_sin_datos);
        }
    }

    private String obtenerTextoEstado(String estado) {
        switch (estado) {
            case Municipio.EstadoNorma.PERMITIDO:   return "✅ PERMITIDO";
            case Municipio.EstadoNorma.RESTRINGIDO: return "⚠️ RESTRINGIDO";
            case Municipio.EstadoNorma.PROHIBIDO:   return "🚫 PROHIBIDO";
            default:                                return "❓ SIN DATOS";
        }
    }

}