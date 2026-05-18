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
    // 5km es razonable para municipios: detecta la entrada con margen
    private static final float  RADIO_METROS = 5000f;

    private final Context          context;
    private final GeofencingClient geofencingClient;
    private       PendingIntent    geofencePendingIntent;

    public GeofenceManager(Context context) {
        this.context          = context.getApplicationContext();
        this.geofencingClient = LocationServices.getGeofencingClient(this.context);
    }

    /**
     * Registra un geofence por cada municipio en la lista.
     * Cuando el usuario entre en el radio de cualquiera,
     * Android disparará el GeofenceBroadcastReceiver.
     *
     * IMPORTANTE: requiere permiso ACCESS_FINE_LOCATION concedido.
     * La anotación suprime el warning: ya verificamos el permiso
     * antes de llamar a este método desde MainActivity.
     */
    @SuppressWarnings("MissingPermission")
    public void registrarGeofences(List<Municipio> municipios) {
        if (municipios == null || municipios.isEmpty()) return;

        List<Geofence> geofences = new ArrayList<>();

        for (Municipio municipio : municipios) {
            geofences.add(new Geofence.Builder()
                    // ID único: usamos el código INE para poder identificarlo
                    // en el BroadcastReceiver y buscar el municipio en la BD
                    .setRequestId(municipio.getCodigoIne())
                    .setCircularRegion(
                            municipio.getLatitud(),
                            municipio.getLongitud(),
                            RADIO_METROS
                    )
                    // NEVER_EXPIRE → el geofence no caduca solo
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    // Queremos saber cuando ENTRA (y opcionalmente cuando SALE)
                    .setTransitionTypes(
                            Geofence.GEOFENCE_TRANSITION_ENTER |
                                    Geofence.GEOFENCE_TRANSITION_EXIT
                    )
                    .build()
            );
        }

        GeofencingRequest request = new GeofencingRequest.Builder()
                // INITIAL_TRIGGER_ENTER → si ya estás dentro al registrar, avisa
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build();

        geofencingClient
                .addGeofences(request, obtenerPendingIntent())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Geofences registrados: " + geofences.size()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Error registrando geofences: " + e.getMessage()));
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