package com.tupackage.radardenormativas.ui.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.tupackage.radardenormativas.MainActivity;
import com.tupackage.radardenormativas.R;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

public class NotificationHelper {

    // IDs constantes: Android los usa para actualizar/cancelar notificaciones
    private static final String CHANNEL_ID   = "canal_normativas";
    private static final String CHANNEL_NAME = "Alertas de Normativa";
    private static final int    NOTIF_ID     = 1001;

    /**
     * Llama a esto UNA VEZ al arrancar la app (en MainActivity.onCreate).
     * Sin el canal creado, las notificaciones no aparecen en Android 8+.
     * En versiones anteriores este método no hace nada (el if lo controla).
     */
    public static void crearCanalNotificaciones(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    // IMPORTANCE_HIGH → aparece en pantalla aunque el móvil esté bloqueado
                    NotificationManager.IMPORTANCE_HIGH
            );
            canal.setDescription("Avisos sobre normativa de pernocta al entrar en un municipio");

            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(canal);
        }
    }

    /**
     * Lanza la notificación con la info del municipio detectado.
     *
     * @param municipio El municipio en cuyo radio ha entrado el usuario
     */
    public static void lanzarNotificacionMunicipio(Context context, Municipio municipio) {

        // Al pulsar la notificación, abre MainActivity
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // PendingIntent → envuelve el Intent para que Android pueda
        // lanzarlo más tarde (cuando el usuario toca la notificación)
        // FLAG_IMMUTABLE → obligatorio desde Android 12
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Elegimos el icono y color según el estado de la norma
        int colorIndicador = obtenerColorEstado(context, municipio.getEstadoNorma());
        String emoji       = obtenerEmojiEstado(municipio.getEstadoNorma());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)   // ← lo crearemos ahora
                .setContentTitle(emoji + " " + municipio.getNombre())
                .setContentText(municipio.getResumenCorto())
                // BigTextStyle → muestra el texto completo al expandir la notificación
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(municipio.getResumenCorto())
                        .setSummaryText("Normativa de pernocta"))
                .setColor(colorIndicador)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);  // Se descarta al tocarla

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIF_ID, builder.build());
    }

    private static int obtenerColorEstado(Context context, String estadoNorma) {
        switch (estadoNorma) {
            case Municipio.EstadoNorma.PERMITIDO:
                return context.getColor(R.color.estado_permitido);
            case Municipio.EstadoNorma.PROHIBIDO:
                return context.getColor(R.color.estado_prohibido);
            case Municipio.EstadoNorma.RESTRINGIDO:
                return context.getColor(R.color.estado_restringido);
            default:
                return context.getColor(R.color.estado_sin_datos);
        }
    }

    private static String obtenerEmojiEstado(String estadoNorma) {
        switch (estadoNorma) {
            case Municipio.EstadoNorma.PERMITIDO:    return "🟢";
            case Municipio.EstadoNorma.RESTRINGIDO:  return "🟡";
            case Municipio.EstadoNorma.PROHIBIDO:    return "🔴";
            default:                                  return "⚫";
        }
    }
}