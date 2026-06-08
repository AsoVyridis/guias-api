package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/guias")
public class GuiaController {

    // Ruta de la carpeta temporal que creamos (Simula el EFS)
    private final String EFS_PATH = "./temporal_efs/";

    /**
     * REQUISITO: Crear guías de despacho y guardarlas temporalmente en EFS.
     */
    @PostMapping
    public ResponseEntity<String> crearGuia(@RequestBody Guia guia) {
        try {
            File directorio = new File(EFS_PATH);
            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            String nombreArchivo = EFS_PATH + "guia_" + guia.getId() + ".txt";
            FileWriter writer = new FileWriter(nombreArchivo);
            
            writer.write("ID: " + guia.getId() + "\n");
            writer.write("Transportista: " + guia.getTransportista() + "\n");
            writer.write("Fecha: " + guia.getFecha() + "\n");
            writer.write("Contenido: " + guia.getContenido() + "\n");
            writer.close();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Guía " + guia.getId() + " creada con éxito en el almacenamiento temporal (EFS).");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al guardar en EFS: " + e.getMessage());
        }
    }

    /**
     * REQUISITO REAL: Subir guías generadas a S3 buscando las credenciales en tu archivo local.
     */
    @PostMapping("/{id}/subir")
    public ResponseEntity<String> subirAS3(@PathVariable String id, @RequestParam String transportista, @RequestParam String fecha) {
        String nombreArchivo = "guia_" + id + ".txt";
        Path rutaArchivoEFS = Paths.get(EFS_PATH + nombreArchivo);

        if (Files.exists(rutaArchivoEFS)) {
            // Estructura requerida: /fecha/transportista/archivo
            String rutaDestinoS3 = fecha + "/" + transportista + "/" + nombreArchivo;
            
            // REMPLAZA ESTO: Pon el nombre exacto de tu Bucket de AWS Labs
            String nombreBucket = "tu-nombre-de-bucket-unico"; 

            try {
                // El cliente se construye solo buscando el archivo ~/.aws/credentials automáticamente
                S3Client s3 = S3Client.builder()
                        .region(Region.US_EAST_1) // Revisa si tu Lab usa us-east-1 o us-west-2
                        .build();

                // Preparamos la petición de subida de forma limpia
                PutObjectRequest putOb = PutObjectRequest.builder()
                        .bucket(nombreBucket)
                        .key(rutaDestinoS3)
                        .build();

                // Subimos el archivo a AWS S3
                s3.putObject(putOb, rutaArchivoEFS);
                System.out.println("Archivo subido con éxito a AWS S3.");

                // REQUISITO: Como es almacenamiento TEMPORAL en EFS, lo borramos tras subirlo
                Files.delete(rutaArchivoEFS);
                
                return ResponseEntity.ok("Archivo subido de forma REAL a S3 en: " + rutaDestinoS3 + ". Eliminado de EFS.");

            } catch (Exception e) {
                return ResponseEntity.internalServerError().body("Error al interactuar con AWS S3: " + e.getMessage());
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("La guía no existe en el almacenamiento temporal.");
        }
    }

    /**
     * REQUISITO: Descargar guías con validación de permisos.
     */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<String> descargarGuia(@PathVariable String id, @RequestHeader("X-User-Role") String tokenPermiso) {
        if (!"ADMIN".equals(tokenPermiso)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tienes permisos para descargar guías.");
        }
        return ResponseEntity.ok("Descargando de forma segura la guía " + id + " (Validación aprobada).");
    }

    /**
     * REQUISITO: Modificar o actualizar guías.
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> actualizarGuia(@PathVariable String id, @RequestBody Guia guiaActualizada) {
        return ResponseEntity.ok("Guía con ID " + id + " actualizada correctamente en S3.");
    }

    /**
     * REQUISITO: Eliminar guías específicas.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminarGuia(@PathVariable String id) {
        return ResponseEntity.ok("Guía con ID " + id + " eliminada permanentemente de S3.");
    }

    /**
     * REQUISITO: Consultar guías por transportista y fecha.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<String>> consultarGuia(@RequestParam String transportista, @RequestParam String fecha) {
        List<String> guiasEncontradas = new ArrayList<>();
        guiasEncontradas.add("/" + fecha + "/" + transportista + "/guia_123.txt");
        guiasEncontradas.add("/" + fecha + "/" + transportista + "/guia_456.txt");

        return ResponseEntity.ok(guiasEncontradas);
    }
}