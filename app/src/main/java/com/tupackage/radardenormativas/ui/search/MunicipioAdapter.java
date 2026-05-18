package com.tupackage.radardenormativas.ui.search;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.tupackage.radardenormativas.R;
import com.tupackage.radardenormativas.data.local.entity.Municipio;

/**
 * ListAdapter → versión mejorada de RecyclerView.Adapter.
 * Usa DiffUtil internamente para animar solo los items que cambian,
 * en lugar de redibujar la lista entera con notifyDataSetChanged().
 */
public class MunicipioAdapter
        extends ListAdapter<Municipio, MunicipioAdapter.MunicipioViewHolder> {

    // Interfaz para notificar clics a la Activity
    public interface OnMunicipioClickListener {
        void onMunicipioClick(Municipio municipio);
    }

    private final OnMunicipioClickListener listener;

    public MunicipioAdapter(OnMunicipioClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    /**
     * DiffUtil compara items para saber cuáles han cambiado.
     * areItemsTheSame → ¿es el mismo municipio? (por ID)
     * areContentsTheSame → ¿han cambiado sus datos?
     */
    private static final DiffUtil.ItemCallback<Municipio> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Municipio>() {
                @Override
                public boolean areItemsTheSame(@NonNull Municipio a, @NonNull Municipio b) {
                    return a.getCodigoIne().equals(b.getCodigoIne());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Municipio a, @NonNull Municipio b) {
                    return a.getCodigoIne().equals(b.getCodigoIne())
                            && a.getEstadoNorma().equals(b.getEstadoNorma())
                            && a.getNombre().equals(b.getNombre());
                }
            };

    @NonNull
    @Override
    public MunicipioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_municipio, parent, false);
        return new MunicipioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MunicipioViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class MunicipioViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvNombre;
        private final TextView tvProvincia;
        private final TextView tvEstado;
        private final View     viewColor;

        MunicipioViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre    = itemView.findViewById(R.id.tv_item_nombre);
            tvProvincia = itemView.findViewById(R.id.tv_item_provincia);
            tvEstado    = itemView.findViewById(R.id.tv_item_estado);
            viewColor   = itemView.findViewById(R.id.view_item_color);
        }

        void bind(Municipio municipio, OnMunicipioClickListener listener) {
            tvNombre.setText(municipio.getNombre());
            tvProvincia.setText(municipio.getProvincia());
            tvEstado.setText(obtenerTextoEstado(municipio.getEstadoNorma()));

            int color = obtenerColor(municipio.getEstadoNorma());
            viewColor.setBackgroundTintList(ColorStateList.valueOf(color));

            android.graphics.drawable.GradientDrawable bg =
                    (android.graphics.drawable.GradientDrawable) tvEstado.getBackground();
            bg.setColor(color);

            // Clic en toda la tarjeta
            itemView.setOnClickListener(v -> listener.onMunicipioClick(municipio));
        }

        private int obtenerColor(String estado) {
            android.content.Context ctx = itemView.getContext();
            switch (estado) {
                case Municipio.EstadoNorma.PERMITIDO:
                    return ctx.getColor(R.color.estado_permitido);
                case Municipio.EstadoNorma.RESTRINGIDO:
                    return ctx.getColor(R.color.estado_restringido);
                case Municipio.EstadoNorma.PROHIBIDO:
                    return ctx.getColor(R.color.estado_prohibido);
                default:
                    return ctx.getColor(R.color.estado_sin_datos);
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
}