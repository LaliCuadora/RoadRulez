package com.tupackage.radardenormativas.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.tupackage.radardenormativas.data.local.dao.MunicipioDao;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
        entities = { Municipio.class },
        version  = 1,
        exportSchema = false
)
public abstract class RadarDatabase extends RoomDatabase {

    public abstract MunicipioDao municipioDao();

    private static volatile RadarDatabase INSTANCE;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(4);

    public static RadarDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RadarDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            RadarDatabase.class,
                            "radar_normativas_db"
                    ).build(); // ← sin .addCallback(), el SyncManager se encarga
                }
            }
        }
        return INSTANCE;
    }
}