package com.countpipes.mcat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.Intent
import android.widget.Button



class RegistrosActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registros)

        val tableLayout = findViewById<TableLayout>(R.id.tableLayoutRegistros)

        val registros = leerCSV()
        for (registro in registros) {
            agregarFila(tableLayout, registro)
        }

        val btnCompartir = findViewById<Button>(R.id.btnCompartirCSV)
        btnCompartir.setOnClickListener {
            compartirCSV()
        }



    }

    private fun leerCSV(): List<Registro> {
        val registros = mutableListOf<Registro>()
        val file = getCSVFile()  // CSV en Downloads

        if (file.exists()) {
            val lines = file.readLines() // Convertimos a lista
            lines.forEachIndexed { index, line ->
                if (index == 0) return@forEachIndexed // Ignorar encabezado
                val parts = line.split(",")
                if (parts.size >= 7) {
                    registros.add(
                        Registro(
                            fecha = parts[0],
                            centro = parts[1],
                            ubicacion = parts[2],
                            material = parts[3],
                            descripcion = parts[4],
                            cantidad = parts[5].toIntOrNull() ?: 0,
                            imagen = parts[6]
                        )
                    )
                }
            }
        }
        return registros
    }


    private fun agregarFila(tabla: TableLayout, registro: Registro) {
        val fila = TableRow(this)

        val params = TableRow.LayoutParams(
            TableRow.LayoutParams.WRAP_CONTENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(2, 2, 2, 2)

        // Anchos mínimos por columna
        val minWidths = arrayOf(120, 120, 150, 120, 180, 100)

        val valores = listOf(
            registro.fecha,
            registro.centro,
            registro.ubicacion,
            registro.material,
            registro.descripcion,
            registro.cantidad.toString()
        )

        for ((index, valor) in valores.withIndex()) {
            val textView = TextView(this)
            textView.text = valor
            textView.setPadding(12, 12, 12, 12)
            textView.gravity = android.view.Gravity.CENTER
            textView.minWidth = minWidths.getOrElse(index) { 120 }
            textView.setBackgroundResource(R.drawable.celda_border)
            textView.setTextColor(android.graphics.Color.BLACK)
            textView.layoutParams = params
            fila.addView(textView)
        }

        tabla.addView(fila)
    }

    private fun guardarCSVEnDescargas(nombreArchivo: String, registros: List<Registro>) {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!path.exists()) path.mkdirs()

        val file = File(path, nombreArchivo)
        file.printWriter().use { out ->
            registros.forEach { registro ->
                out.println("${registro.fecha},${registro.centro},${registro.ubicacion},${registro.material},${registro.descripcion},${registro.cantidad}")
            }
        }

        Toast.makeText(this, "CSV guardado en Descargas: ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun getCSVFile(): File {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!path.exists()) path.mkdirs()
        return File(path, "conteos.csv")
    }


    private fun compartirCSV() {
        val file = getCSVFile()
        if (!file.exists()) {
            Toast.makeText(this, "Archivo no encontrado", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Conteos de tubos")
        }
        startActivity(Intent.createChooser(intent, "Enviar CSV"))
    }




}
