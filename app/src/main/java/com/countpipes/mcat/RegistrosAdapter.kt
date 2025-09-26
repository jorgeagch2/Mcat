package com.countpipes.mcat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RegistrosAdapter(private val registros: List<Registro>) :
    RecyclerView.Adapter<RegistrosAdapter.RegistroViewHolder>() {

    class RegistroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textFecha: TextView = itemView.findViewById(R.id.textFecha)
        val textCentro: TextView = itemView.findViewById(R.id.textCentro)
        val textUbicacion: TextView = itemView.findViewById(R.id.textUbicacion)
        val textMaterial: TextView = itemView.findViewById(R.id.textMaterial)
        val textDescripcion: TextView = itemView.findViewById(R.id.textDescripcion)
        val textCantidad: TextView = itemView.findViewById(R.id.textCantidad)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegistroViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_registro, parent, false) // 👈 este será tu layout de cada ítem
        return RegistroViewHolder(view)
    }

    override fun onBindViewHolder(holder: RegistroViewHolder, position: Int) {
        val registro = registros[position]
        holder.textFecha.text = registro.fecha
        holder.textCentro.text = registro.centro
        holder.textUbicacion.text = registro.ubicacion
        holder.textMaterial.text = registro.material
        holder.textDescripcion.text = registro.descripcion
        holder.textCantidad.text = registro.cantidad.toString()
    }

    override fun getItemCount(): Int = registros.size
}
