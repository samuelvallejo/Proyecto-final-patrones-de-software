package com.streamguard.core;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
  private final Path root;
  private final String ffmpeg, ffprobe;
  private final Db db;

  public MediaService(
      Db db,
      @Value("${app.media-dir}") String directory,
      @Value("${FFMPEG_BIN:ffmpeg}") String ffmpeg,
      @Value("${FFPROBE_BIN:ffprobe}") String ffprobe)
      throws IOException {
    this.db = db;
    this.root = Path.of(directory).toAbsolutePath().normalize();
    this.ffmpeg = ffmpeg;
    this.ffprobe = ffprobe;
    Files.createDirectories(root);
  }

  public Path path(String key) {
    if (!key.matches("[a-f0-9-]+\\.webm")) throw new ApiError(404, "Archivo no encontrado");
    Path p = root.resolve(key).normalize();
    if (!p.startsWith(root)) throw new ApiError(404, "Archivo no encontrado");
    return p;
  }

  public record Saved(UUID asset, String key, double duration) {}

  public Saved save(UUID owner, MultipartFile file) {
    if (file.isEmpty() || file.getSize() > 30 * 1024 * 1024)
      throw new ApiError(400, "El clip debe tener entre 1 byte y 30 MB");
    String key = UUID.randomUUID() + ".webm";
    Path destination = path(key);
    try {
      boolean mp4;
      try (InputStream input = file.getInputStream()) {
        byte[] magic = input.readNBytes(12);
        boolean webm =
            magic.length >= 4
                && magic[0] == (byte) 0x1A
                && magic[1] == (byte) 0x45
                && magic[2] == (byte) 0xDF
                && magic[3] == (byte) 0xA3;
        mp4 =
            magic.length >= 8
                && new String(magic, 4, 4, java.nio.charset.StandardCharsets.US_ASCII)
                    .equals("ftyp");
        if (!webm && !mp4) throw new ApiError(400, "Se requiere una grabación WebM o MP4 válida");
      }
      if (mp4) {
        Path original = root.resolve(UUID.randomUUID() + ".mp4");
        try {
          file.transferTo(original);
          run(
              List.of(
                  ffmpeg,
                  "-v",
                  "error",
                  "-y",
                  "-i",
                  original.toString(),
                  "-c:v",
                  "libvpx-vp9",
                  "-deadline",
                  "realtime",
                  "-cpu-used",
                  "8",
                  "-c:a",
                  "libopus",
                  destination.toString()),
              45);
        } finally {
          delete(original);
        }
      } else file.transferTo(destination);
      double duration = probe(destination);
      if (duration <= 0 || duration > 65)
        throw new ApiError(400, "La grabación debe durar entre 0 y 65 segundos");
      UUID asset =
          db.insert(
              "INSERT INTO media_assets(owner_id,storage_key,mime_type,size_bytes,duration_seconds)"
                  + " VALUES (?,?,'video/webm',?,?) RETURNING id",
              owner,
              key,
              Files.size(destination),
              duration);
      return new Saved(asset, key, duration);
    } catch (ApiError e) {
      delete(destination);
      throw e;
    } catch (Exception e) {
      delete(destination);
      throw new ApiError(
          503, "No se pudo procesar el video. Verifica FFmpeg y el volumen de almacenamiento");
    }
  }

  private double probe(Path p) throws Exception {
    String output =
        run(
            List.of(
                ffprobe,
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                p.toString()),
            15);
    // MediaRecorder may omit duration; ffmpeg remux repairs timestamps/metadata.
    if (output.strip().equals("N/A") || output.isBlank()) {
      Path fixed = path(UUID.randomUUID() + ".webm");
      try {
        run(
            List.of(
                ffmpeg, "-v", "error", "-y", "-i", p.toString(), "-c", "copy", fixed.toString()),
            30);
        Files.move(fixed, p, StandardCopyOption.REPLACE_EXISTING);
        output =
            run(
                List.of(
                    ffprobe,
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    p.toString()),
                15);
      } finally {
        delete(fixed);
      }
    }
    double d = Double.parseDouble(output.strip());
    if (!Double.isFinite(d)) throw new IllegalArgumentException();
    return d;
  }

  public Saved trim(UUID owner, String sourceKey, double start, double end) {
    Path dest = path(UUID.randomUUID() + ".webm");
    try {
      run(
          List.of(
              ffmpeg,
              "-v",
              "error",
              "-y",
              "-ss",
              Double.toString(start),
              "-i",
              path(sourceKey).toString(),
              "-t",
              Double.toString(end - start),
              "-c:v",
              "libvpx-vp9",
              "-deadline",
              "realtime",
              "-cpu-used",
              "8",
              "-c:a",
              "libopus",
              dest.toString()),
          45);
      double d = probe(dest);
      UUID id =
          db.insert(
              "INSERT INTO media_assets(owner_id,storage_key,mime_type,size_bytes,duration_seconds)"
                  + " VALUES (?,?,'video/webm',?,?) RETURNING id",
              owner,
              dest.getFileName().toString(),
              Files.size(dest),
              d);
      return new Saved(id, dest.getFileName().toString(), d);
    } catch (Exception e) {
      delete(dest);
      throw new ApiError(503, "No se pudo recortar el clip con FFmpeg");
    }
  }

  private String run(List<String> args, int timeout) throws Exception {
    var process = new ProcessBuilder(args).redirectErrorStream(true).start();
    // Consume output while waiting so a filled pipe cannot deadlock the encoder.
    var output = new ByteArrayOutputStream();
    Thread reader =
        Thread.startVirtualThread(
            () -> {
              try {
                process.getInputStream().transferTo(output);
              } catch (IOException ignored) {
              }
            });
    if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IOException("Timeout");
    }
    reader.join(Duration.ofSeconds(2));
    if (process.exitValue() != 0) throw new IOException("Invalid media");
    return output.toString(java.nio.charset.StandardCharsets.UTF_8);
  }

  private void delete(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException ignored) {
    }
  }

  public Path authorized(UUID asset, UUID user) {
    var row =
        db.one(
            "SELECT a.storage_key,a.owner_id,EXISTS(SELECT 1 FROM clips c WHERE c.asset_id=a.id AND"
                + " c.status='APPROVED') AS published FROM media_assets a WHERE a.id=?",
            asset);
    if (!Objects.equals(row.get("owner_id"), user) && !(boolean) row.get("published"))
      throw new ApiError(403, "El creador aún no ha publicado este clip");
    Path p = path(row.get("storage_key").toString());
    if (!Files.exists(p)) throw new ApiError(404, "El archivo ya no está disponible");
    return p;
  }
}
