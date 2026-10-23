package com.nitix.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Salva fotos e vídeos na galeria (Pictures/Nitix e Movies/Nitix). */
final class MediaSaver {
    private MediaSaver() {}

    static Uri saveImage(Context c, Bitmap b, String baseName) throws IOException {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".jpg");
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Nitix");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("Não consegui criar o arquivo");
        try (OutputStream out = cr.openOutputStream(uri)) {
            if (out == null || !b.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                throw new IOException("Falha ao gravar a foto");
            }
        }
        finish(cr, uri);
        return uri;
    }

    static Uri saveVideo(Context c, File src, String baseName) throws IOException {
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, baseName + ".mp4");
        v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= 29) {
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Nitix");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        }
        Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new IOException("Não consegui criar o arquivo");
        try (InputStream in = new FileInputStream(src); OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new IOException("Falha ao gravar o vídeo");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        finish(cr, uri);
        return uri;
    }

    private static void finish(ContentResolver cr, Uri uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, done, null, null);
        }
    }
}
