package com.example.demo;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/inscripciones")
public class InscripcionController {

    private final String EFS_PATH = "./temporal_efs/";

    private S3Client getS3Client() {
        String regionEnv = System.getenv("AWS_REGION");
        Region region = (regionEnv != null) ? Region.of(regionEnv) : Region.US_EAST_1;
        // El S3Client.builder() por defecto buscará las credenciales en ~/.aws/credentials o variables de entorno
        return S3Client.builder().region(region).build();
    }

    private String getBucketName() {
        return System.getenv("BUCKET_NAME");
    }

    /**
     * 1. Crear resumen de inscripción físico localmente
     */
    @PostMapping
    public ResponseEntity<String> crearInscripcion(@RequestBody Inscripcion inscripcion) {
        try {
            File directorio = new File(EFS_PATH);
            if (!directorio.exists()) {
                directorio.mkdirs();
            }

            String nombreArchivo = "inscripcion_" + inscripcion.getNumeroResumen() + ".txt";
            FileWriter writer = new FileWriter(EFS_PATH + nombreArchivo);
            
            writer.write("Resumen de Inscripción: " + inscripcion.getNumeroResumen() + "\n");
            writer.write("Estudiante: " + inscripcion.getNombreEstudiante() + "\n");
            writer.write("Curso: " + inscripcion.getCurso() + "\n");
            writer.write("Fecha: " + inscripcion.getFecha() + "\n");
            writer.write("Detalles: " + inscripcion.getDetalles() + "\n");
            writer.close();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Resumen físico creado con éxito en: " + EFS_PATH + nombreArchivo);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el archivo físico: " + e.getMessage());
        }
    }

    /**
     * 2. Subir el resumen de inscripción generado a AWS S3
     */
    @PostMapping("/{numero}/subir")
    public ResponseEntity<String> subirAS3(@PathVariable String numero) {
        String nombreArchivo = "inscripcion_" + numero + ".txt";
        Path rutaArchivoEFS = Paths.get(EFS_PATH + nombreArchivo);

        if (!Files.exists(rutaArchivoEFS)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("El resumen físico no existe. Créalo primero.");
        }

        String nombreBucket = getBucketName();
        if (nombreBucket == null || nombreBucket.isEmpty()) {
            return ResponseEntity.internalServerError().body("Variable de entorno BUCKET_NAME no configurada.");
        }

        // Estructura requerida: carpeta con el número del resumen
        String rutaDestinoS3 = numero + "/" + nombreArchivo;

        try {
            S3Client s3 = getS3Client();
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(nombreBucket)
                    .key(rutaDestinoS3)
                    .build();

            s3.putObject(putOb, rutaArchivoEFS);

            // Eliminar de local tras subir
            Files.delete(rutaArchivoEFS);

            return ResponseEntity.ok("Resumen subido exitosamente a S3 en: " + rutaDestinoS3);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al subir a S3: " + e.getMessage());
        }
    }

    /**
     * 3. Descargar el archivo del resumen de inscripción desde S3
     */
    @GetMapping("/{numero}/descargar")
    public ResponseEntity<byte[]> descargarResumenS3(@PathVariable String numero) {
        String nombreBucket = getBucketName();
        String rutaObjetoS3 = numero + "/inscripcion_" + numero + ".txt";

        try {
            S3Client s3 = getS3Client();
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(nombreBucket)
                    .key(rutaObjetoS3)
                    .build();

            ResponseBytes<GetObjectResponse> objectBytes = s3.getObjectAsBytes(getObjectRequest);
            byte[] data = objectBytes.asByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", "inscripcion_" + numero + ".txt");

            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (NoSuchKeyException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * 4. Modificar el archivo del resumen de la inscripción directamente (Re-subir un string)
     */
    @PutMapping("/{numero}")
    public ResponseEntity<String> modificarResumenS3(@PathVariable String numero, @RequestBody String nuevoContenido) {
        String nombreBucket = getBucketName();
        String rutaDestinoS3 = numero + "/inscripcion_" + numero + ".txt";

        try {
            S3Client s3 = getS3Client();
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(nombreBucket)
                    .key(rutaDestinoS3)
                    .build();

            s3.putObject(putOb, software.amazon.awssdk.core.sync.RequestBody.fromString(nuevoContenido));
            
            return ResponseEntity.ok("El resumen de inscripción " + numero + " fue modificado exitosamente en S3.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al modificar en S3: " + e.getMessage());
        }
    }

    /**
     * 5. Borrar el archivo del resumen de inscripción en S3
     */
    @DeleteMapping("/{numero}")
    public ResponseEntity<String> borrarResumenS3(@PathVariable String numero) {
        String nombreBucket = getBucketName();
        String rutaObjetoS3 = numero + "/inscripcion_" + numero + ".txt";

        try {
            S3Client s3 = getS3Client();
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(nombreBucket)
                    .key(rutaObjetoS3)
                    .build();

            s3.deleteObject(deleteObjectRequest);
            return ResponseEntity.ok("El resumen de inscripción " + numero + " fue borrado exitosamente de S3.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al borrar de S3: " + e.getMessage());
        }
    }
}
