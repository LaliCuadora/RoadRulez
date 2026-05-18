package com.tupackage.radardenormativas.geofencing;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.local.entity.Municipio;
import com.tupackage.radardenormativas.ui.notifications.NotificationHelper;

import java.util.List;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "GeofenceReceiver";

    /**
     * Android llama a onReceive() en el Main Thread y de forma muy breve.
     * Si necesitas hacer trabajo largo (consulta a BD), usa un hilo separado.
     * El goAsync() extiende el tiempo disponible, pero aquí usaremos
     * directamente el Executor de Room que ya tenemos configurado.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);

        if (event == null || event.hasError()) {
            Log.e(TAG, "Error en GeofencingEvent");
            return;
        }

        int tipoTransicion = event.getGeofenceTransition();

        // Solo actuamos cuando el usuario ENTRA en el área
        if (tipoTransicion == Geofence.GEOFENCE_TRANSITION_ENTER) {

            List<Geofence> geofencesDisparados = event.getTriggeringGeofences();
            if (geofencesDisparados == null || geofencesDisparados.isEmpty()) return;

            // Tomamos el primer geofence disparado (normalmente solo uno)
            String codigoIne = geofencesDisparados.get(0).getRequestId();
            Log.d(TAG, "Entrando en municipio con código INE: " + codigoIne);

            // Consultamos la BD en background para obtener los datos del municipio
            RadarDatabase.databaseWriteExecutor.execute(() -> {
                Municipio municipio = RadarDatabase
                        .getInstance(context)
                        .municipioDao()
                        .obtenerPorCodigoIneSync(codigoIne);

                if (municipio != null) {
                    // Lanzamos la notificación (se puede llamar desde cualquier hilo)
                    NotificationHelper.lanzarNotificacionMunicipio(context, municipio);
                    Log.d(TAG, "Notificación lanzada para: " + municipio.getNombre());
                }
            });

        } else if (tipoTransicion == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // Opcional: notificar que se ha salido del municipio
            Log.d(TAG, "Saliendo de un área de geofence");
        }
    }
}