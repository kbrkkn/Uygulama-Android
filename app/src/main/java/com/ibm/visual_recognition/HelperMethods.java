package com.ibm.visual_recognition;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.IOException;

/**
 * Created by kubra on 22.09.2017.
 */

public class HelperMethods {
    //Watson maximum 1200 pixellik resimleri kabul ediyor.Bu yüzden orijinal resmi yeniden boyutlandırıyoruz.
    //En ya da yükseklikten hangisi daha büyükse bunu sınırlandırıcı olarak kulllanıp,1200'ü bu sayıya bölüyoruz.
    //Bölüm sonucu kadar n*n matrix oluşturuyoruz ve bu bizim resmimizin yeni boyutlarını belirliyor.
    //Yeni matrix ile yeni bir bitmap oluşturuyoruz.
    public static Bitmap resizeBitmapForWatson(Bitmap originalImage, float maxSize) {

        int originalHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();

        int boundingDimension = (originalHeight > originalWidth) ? originalHeight : originalWidth;

        float scale = maxSize / boundingDimension;

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        originalImage = Bitmap.createBitmap(originalImage, 0, 0, originalWidth, originalHeight, matrix, true);

        return originalImage;
    }
    //Uri'ı verilen fotonun,cihazdan bitmap görüntüsünü getirir.
    public static Bitmap fetchBitmapFromUri(Uri imageUri, ContentResolver contentResolver, Context context) {
        try {
//Uri'dan Bitmap'e dönüştürüyoruz
            Bitmap selectedImage = MediaStore.Images.Media.getBitmap(contentResolver, imageUri);
// Telefon hafızasındaki fotonun pozisyonunu alır.
            String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
            Cursor cursor = contentResolver.query(imageUri, orientationColumn, null, null, null);
            int orientation = 0;
            if (cursor != null && cursor.moveToFirst()) {
                orientation = cursor.getInt(cursor.getColumnIndex(orientationColumn[0]));
            }
            if(cursor != null) {
                cursor.close();
            }
//Pozsiyonun ile bitmap yeniden oluşturularak,fotonun doğru pozisyonda durması sağlanır
            Matrix matrix = new Matrix();
            matrix.setRotate(orientation);
            selectedImage = Bitmap.createBitmap(selectedImage, 0, 0, selectedImage.getWidth(), selectedImage.getHeight(), matrix, true);

            return selectedImage;

        } catch (IOException e) {
            Toast.makeText(context,e.getMessage(),Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return null;
        }
    }
}
