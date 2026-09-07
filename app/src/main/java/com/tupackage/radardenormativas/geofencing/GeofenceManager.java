package com.tupackage.radardenormativas.geofencing;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

import java.util.ArrayList;
import java.util.List;

public class GeofenceManager {

    private static final String TAG          = "GeofenceManager";
    // Radio en metros del círculo de cada geofence
    // 1km debido a que con un mayor radio pueden haber errores con municipios cercanos que estén muy pegados
    private static final float  RADIO_METROS = 1000f;

    private final Context          context;
    private final GeofencingClient geofencingClient;
    private       PendingIntent    geofencePendingIntent;

    public GeofenceManager(Context context) {
        this.context          = context.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(this.context);
    }

    /**
     * Registra un geofence por cada municipio en la lista.
     * Google Play Services limita a 100 geofences por aplicación.
     * Si la lista es mayor, se registrarán solo los 100 primeros.
     */
    @SuppressWarnings("MissingPermission")
    public void registrarGeofences(List<Municipio> municipios) {
        if (municipios == null || municipios.isEmpty()) return;

        // Primero eliminamos cualquier geofence previo registrado con nuestro PendingIntent
        // para no acumularlos y superar el límite de 100 de Google Play Services.
        geofencingClient.removeGeofences(obtenerPendingIntent())
                .addOnCompleteListener(task -> {
                    // Una vez eliminados (o si no había), registramos los nuevos
                    hacerRegistroGeofences(municipios);
                });
    }

    @SuppressWarnings("MissingPermission")
    private void hacerRegistroGeofences(List<Municipio> municipios) {
        // Límite de Google Play Services: 100 geofences
        List<Municipio> listaLimitada;
        if (municipios.size() > 100) {
            Log.w(TAG, "Límite de 100 geofences excedido. Registrando solo los primeros 100 de " + municipios.size());
            listaLimitada = municipios.subList(0, 100);
        } else {
            listaLimitada = municipios;
        }

        List<Geofence> geofences = new ArrayList<>();

        for (Municipio municipio : listaLimitada) {
            geofences.add(new Geofence.Builder()
                    .setRequestId(municipio.getCodigoIne())
                    .setCircularRegion(
                            municipio.getLatitud(),
                            municipio.getLongitud(),
                            RADIO_METROS
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER |
                                    Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            );
        }

        GeofencingRequest request = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();

        geofencingClient
                .addGeofences(request, obtenerPendingIntent())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Geofences registrados con éxito: " + geofences.size()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error crítico registrando geofences: " + e.getMessage()));
    }

    /** Cancela todos los geofences activos. Llámalo en onDestroy si es necesario. */
    public void cancelarGeofences() {
        geofencingClient
                .removeGeofences(obtenerPendingIntent())
                .addOnSuccessListener(unused -> Log.d(TAG, "Geofences cancelados"))
                .addOnFailureListener(e -> Log.e(TAG, "Error cancelando: " + e.getMessage()));
    }

    /**
     * El PendingIntent apunta al BroadcastReceiver.
     * Android lo usa para saber a quién avisar cuando se dispara un geofence.
     * Lo creamos una sola vez (FLAG_UPDATE_CURRENT lo reutiliza si ya existe).
     */
    private PendingIntent obtenerPendingIntent() {
        if (geofencePendingIntent == null) {
            Intent intent = new Intent(context, GeofenceBroadcastReceiver.class);
            geofencePendingIntent = PendingIntent.getBroadcast(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
        }
        return geofencePendingIntent;
    }
}