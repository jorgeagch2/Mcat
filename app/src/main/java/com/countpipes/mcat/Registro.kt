package com.countpipes.mcat

data class Registro(
    val fecha: String,
    val centro: String,
    val ubicacion: String,
    val material: String,
    val descripcion: String,
    val cantidad: Int,
    val imagen: String? = null // opcional, por si guardas nombre de archivo
)
