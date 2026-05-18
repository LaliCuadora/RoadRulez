package com.tupackage.radardenormativas;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.tupackage.radardenormativas.data.local.RadarDatabase;
import com.tupackage.radardenormativas.data.remote.SyncManager;

public class RadarApplication extends Application {

    public static final String PREFS_NAME  = "radar_prefs";
    public static final String KEY_TEMA    = "tema_seleccionado";

    // Valores posibles para KEY_TEMA
    public static final int TEMA_OSCURO  = AppCompatDelegate.MODE_NIGHT_YES;
    public static final int TEMA_CLARO   = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int TEMA_SISTEMA = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;

    @Override
    public void onCreate() {
        super.onCreate();

        // Tema
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int temaGuardado = prefs.getInt(KEY_TEMA, TEMA_OSCURO);
        AppCompatDelegate.setDefaultNightMode(temaGuardado);

        // ← NUEVO: sincronización en background al arrancar
        // Usamos el mismo Executor de Room para no crear hilos extra
        RadarDatabase.databaseWriteExecutor.execute(() -> {
            SyncManager syncManager = new SyncManager(this);
            syncManager.sincronizar();
        });
    }

    /** Cambia el tema y lo guarda para la próxima vez que abra la app */
    public static void cambiarTema(android.content.Context context, int nuevoTema) {
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(KEY_TEMA, nuevoTema)
                .apply();

        AppCompatDelegate.setDefaultNightMode(nuevoTema);
        // Android recrea automáticamente la Activity activa con el nuevo tema
    }
}