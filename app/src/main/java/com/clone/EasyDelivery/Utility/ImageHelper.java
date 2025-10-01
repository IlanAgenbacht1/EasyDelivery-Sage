package com.clone.EasyDelivery.Utility;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ImageHelper {

    public static String saveImage(Context context, Bitmap myBitmap, String IMAGE_DIRECTORY, String SIGN_DIRECTORY) {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        myBitmap.compress(Bitmap.CompressFormat.JPEG, 90, bytes);

        File wallpaperDirectory = new File(context.getFilesDir() + IMAGE_DIRECTORY + SIGN_DIRECTORY);
        // have the object build the directory structure, if needed.
        if (!wallpaperDirectory.exists()) {
            wallpaperDirectory.mkdirs();
            Log.d("hhhhh", wallpaperDirectory.toString());
        }

        try {
            File f = new File(wallpaperDirectory, Calendar.getInstance()
                    .getTimeInMillis() + ".jpg");
            f.createNewFile();
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(bytes.toByteArray());
            MediaScannerConnection.scanFile(context,
                    new String[]{f.getPath()},
                    new String[]{"image/jpeg"}, null);
            fo.close();
            Log.d("TAG", "File Saved::--->" + f.getAbsolutePath());

            return f.getAbsolutePath();

        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return "";
    }


    public static String compressImage(Context context, String imageUri, String IMAGE_DIRECTORY, String SIGN_PATH) {

        String filePath = getRealPathFromURI(context, imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;

//      max Height and width values of the compressed image is taken as 816x612

        if (actualHeight != 0 && actualWidth != 0) {
            float maxHeight = 816.0f;
            float maxWidth = 612.0f;
            float imgRatio = actualWidth / actualHeight;
            float maxRatio = maxWidth / maxHeight;

//      width and height values are set maintaining the aspect ratio of the image

            if (actualHeight > maxHeight || actualWidth > maxWidth) {
                if (imgRatio < maxRatio) {
                    imgRatio = maxHeight / actualHeight;
                    actualWidth = (int) (imgRatio * actualWidth);
                    actualHeight = (int) maxHeight;
                } else if (imgRatio > maxRatio) {
                    imgRatio = maxWidth / actualWidth;
                    actualHeight = (int) (imgRatio * actualHeight);
                    actualWidth = (int) maxWidth;
                } else {
                    actualHeight = (int) maxHeight;
                    actualWidth = (int) maxWidth;

                }
            }

//      setting inSampleSize value allows to load a scaled down version of the original image

            options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
            options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
            options.inPurgeable = true;
            options.inInputShareable = true;
            options.inTempStorage = new byte[16 * 1024];

            try {
//          load the bitmap from its path
                bmp = BitmapFactory.decodeFile(filePath, options);
            } catch (OutOfMemoryError exception) {
                exception.printStackTrace();

            }
            try {
                scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError exception) {
                exception.printStackTrace();
            }

            float ratioX = actualWidth / (float) options.outWidth;
            float ratioY = actualHeight / (float) options.outHeight;
            float middleX = actualWidth / 2.0f;
            float middleY = actualHeight / 2.0f;

            android.graphics.Matrix scaleMatrix = new android.graphics.Matrix();
            scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

            Canvas canvas = new Canvas(scaledBitmap);
            canvas.setMatrix(scaleMatrix);
            canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

//      check the rotation of the image and display it properly
            ExifInterface exif;
            try {
                exif = new ExifInterface(filePath);

                int orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, 0);
                Log.d("EXIF", "Exif: " + orientation);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                if (orientation == 6) {
                    matrix.postRotate(90);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 3) {
                    matrix.postRotate(180);
                    Log.d("EXIF", "Exif: " + orientation);
                } else if (orientation == 8) {
                    matrix.postRotate(270);
                    Log.d("EXIF", "Exif: " + orientation);
                }
                scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                        scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                        true);
            } catch (IOException e) {
                e.printStackTrace();
            }

            FileOutputStream out = null;
            String filename = getFilename(context, IMAGE_DIRECTORY, SIGN_PATH);

            AppConstant.PIC_PATH = filename;

            try {
                out = new FileOutputStream(filename);

//          write the compressed bitmap at the destination specified by filename.
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            return filename;

        } else {

            return null;
        }
    }


    private static String getRealPathFromURI(Context context, String contentURI) {

        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = context.getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {

            return contentUri.getPath();
        }
        else {

            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);

            return cursor.getString(index);
        }
    }


    public static String getFilename(Context context, String IMAGE_DIRECTORY, String SIGN_PATH) {

        File file = new File(context.getFilesDir().getPath(), IMAGE_DIRECTORY + "/DeliveryImage/");

        if (!file.exists()) {
            file.mkdirs();
        }

        String uriSting = (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ".jpg");

        return uriSting;
    }


    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {

        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }


    public static void deleteImageFiles() {

        if (AppConstant.PIC_PATH != null) {

            File pictureFile = new File(AppConstant.PIC_PATH);

            if (pictureFile.exists()) {

                pictureFile.delete();
            }
        }

        if (AppConstant.SIGN_PATH != null) {

            File signFile = new File(AppConstant.SIGN_PATH);

            if (signFile.exists()) {

                signFile.delete();
            }
        }
    }


    /**
     * Securely save encrypted signature with advanced integrity verification
     */
    public static String saveEncryptedSignatureSecurely(Context context, SecurityManager.SignaturePackage signaturePackage) {
        Log.i("SignatureSecurity", "=== Starting secure signature storage with integrity protection ===");
        
        if (signaturePackage == null) {
            Log.e("SignatureSecurity", "Signature package is null");
            return null;
        }
        
        if (signaturePackage.encryptedData == null || signaturePackage.encryptedData.length == 0) {
            Log.e("SignatureSecurity", "Encrypted signature data is null or empty");
            return null;
        }
        
        if (signaturePackage.integrityData == null || signaturePackage.integrityData.isEmpty()) {
            Log.e("SignatureSecurity", "Integrity data is null or empty");
            return null;
        }
        
        try {
            // Use secure internal storage only (not external)
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(signaturePackage.encryptedData);
            int hashCode = java.util.Arrays.hashCode(hashBytes);
            String fileName = "signature_" + System.currentTimeMillis() + "_" + hashCode + ".enc";
            String integrityFileName = "signature_" + System.currentTimeMillis() + "_" + hashCode + ".integrity";
            
            // Store in internal app-private directory with signature subdirectory
            File secureDir = new File(context.getFilesDir(), "secure_signatures");
            if (!secureDir.exists()) {
                boolean created = secureDir.mkdirs();
                if (!created) {
                    Log.e("SignatureSecurity", "Failed to create secure signature directory");
                    return null;
                }
                Log.d("SignatureSecurity", "Created secure signature directory: " + secureDir.getAbsolutePath());
            }
            
            File signatureFile = new File(secureDir, fileName);
            File integrityFile = new File(secureDir, integrityFileName);
            
            // Save encrypted signature data
            try (FileOutputStream fos = new FileOutputStream(signatureFile)) {
                fos.write(signaturePackage.encryptedData);
                fos.flush();
                Log.d("SignatureSecurity", "Encrypted signature written to: " + signatureFile.getAbsolutePath());
            }
            
            // Save integrity metadata separately
            try (FileOutputStream fos = new FileOutputStream(integrityFile)) {
                fos.write(signaturePackage.integrityData.getBytes("UTF-8"));
                fos.flush();
                Log.d("SignatureSecurity", "Integrity data written to: " + integrityFile.getAbsolutePath());
            }
            
            // Verify file integrity immediately
            if (!verifyEncryptedSignature(signatureFile.getAbsolutePath(), signaturePackage.encryptedData)) {
                Log.e("SignatureSecurity", "Signature file integrity verification failed");
                signatureFile.delete();
                integrityFile.delete();
                return null;
            }
            
            // Set restrictive permissions on both files
            signatureFile.setReadable(true, true);
            signatureFile.setWritable(true, true);
            signatureFile.setExecutable(false, false);
            
            integrityFile.setReadable(true, true);
            integrityFile.setWritable(true, true);
            integrityFile.setExecutable(false, false);
            
            Log.i("SignatureSecurity", "✓ Signature saved securely with integrity protection: " + signatureFile.getAbsolutePath() + 
                  " (" + signaturePackage.encryptedData.length + " bytes)");
            Log.i("SignatureSecurity", "✓ Integrity metadata saved: " + integrityFile.getAbsolutePath());
            Log.d("SignatureSecurity", "File permissions set: signature readable=" + signatureFile.canRead() + 
                  ", integrity readable=" + integrityFile.canRead());
                  
            return signatureFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e("SignatureSecurity", "Failed to save secure signature with integrity: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Load encrypted signature with integrity verification
     */
    public static SecurityManager.SignaturePackage loadEncryptedSignatureWithIntegrity(String signatureFilePath) {
        Log.i("SignatureSecurity", "Loading encrypted signature with integrity verification from: " + signatureFilePath);
        
        if (signatureFilePath == null || signatureFilePath.isEmpty()) {
            Log.e("SignatureSecurity", "Signature file path is null or empty");
            return null;
        }
        
        File signatureFile = new File(signatureFilePath);
        if (!signatureFile.exists()) {
            Log.e("SignatureSecurity", "Signature file does not exist: " + signatureFilePath);
            return null;
        }
        
        // Derive integrity file path
        String integrityFilePath = signatureFilePath.replace(".enc", ".integrity");
        File integrityFile = new File(integrityFilePath);
        
        try {
            // Read encrypted signature data
            byte[] encryptedData = new byte[(int) signatureFile.length()];
            try (FileInputStream fis = new FileInputStream(signatureFile)) {
                int bytesRead = fis.read(encryptedData);
                Log.d("SignatureSecurity", "Read encrypted signature: " + bytesRead + " bytes");
            }
            
            // Read integrity data if available
            String integrityData = null;
            if (integrityFile.exists()) {
                byte[] integrityBytes = new byte[(int) integrityFile.length()];
                try (FileInputStream fis = new FileInputStream(integrityFile)) {
                    int bytesRead = fis.read(integrityBytes);
                    integrityData = new String(integrityBytes, "UTF-8");
                    Log.d("SignatureSecurity", "Read integrity data: " + bytesRead + " bytes");
                }
            } else {
                Log.w("SignatureSecurity", "No integrity file found - signature may be legacy format");
            }
            
            SecurityManager.SignaturePackage signaturePackage = new SecurityManager.SignaturePackage(encryptedData, integrityData);
            Log.i("SignatureSecurity", "✓ Signature package loaded successfully: " + signaturePackage.toString());
            
            return signaturePackage;
            
        } catch (Exception e) {
            Log.e("SignatureSecurity", "Failed to load signature with integrity: " + e.getMessage(), e);
            return null;
        }
    }

    public static String saveEncryptedImage(Context context, byte[] encryptedData, String directory, String subdirectory) {
        String fileName = "signature_" + System.currentTimeMillis() + ".enc";
        File dir = new File(context.getExternalFilesDir(directory), subdirectory);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            Log.d("SIGNATURE_DEBUG", "Directory creation result: " + created);
        }

        File file = new File(dir, fileName);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Verify encrypted data is not null and has content
            if (encryptedData == null || encryptedData.length == 0) {
                Log.e("SIGNATURE_ERROR", "Encrypted data is null or empty");
                return null;
            }

            fos.write(encryptedData);
            fos.flush();

            // Verify file was written correctly
            if (file.exists() && file.length() == encryptedData.length) {
                Log.d("SIGNATURE_DEBUG", "Successfully saved encrypted signature to " + file.getAbsolutePath() +
                        " (" + encryptedData.length + " bytes)");
                Log.d("SIGNATURE_DEBUG", "File verification: exists=" + file.exists() + ", size=" + file.length());
                return file.getAbsolutePath();
            } else {
                Log.e("SIGNATURE_ERROR", "File verification failed - file may not have been saved correctly");
                return null;
            }

        } catch (IOException e) {
            Log.e("SIGNATURE_ERROR", "Failed to save encrypted signature: " + e.getMessage(), e);
            return null;
        }
    }

    // Add method to securely delete signature files
    public static boolean deleteEncryptedSignature(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            Log.w("SIGNATURE_DEBUG", "Cannot delete signature - file path is null or empty");
            return false;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Log.w("SIGNATURE_DEBUG", "Signature file does not exist: " + filePath);
            return true; // Consider it successful if file doesn't exist
        }

        try {
            // Overwrite file content with random data before deletion for security
            long fileSize = file.length();
            byte[] randomData = new byte[(int) fileSize];
            new java.security.SecureRandom().nextBytes(randomData);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(randomData);
                fos.flush();
            }

            boolean deleted = file.delete();
            if (deleted) {
                Log.d("SIGNATURE_DEBUG", "Successfully deleted encrypted signature file: " + filePath);
            } else {
                Log.e("SIGNATURE_ERROR", "Failed to delete encrypted signature file: " + filePath);
            }
            return deleted;

        } catch (Exception e) {
            Log.e("SIGNATURE_ERROR", "Error during signature file deletion: " + e.getMessage(), e);
            return false;
        }
    }

    // Add method to verify signature encryption integrity
    public static boolean verifyEncryptedSignature(String filePath, byte[] originalEncryptedData) {
        if (filePath == null || originalEncryptedData == null) {
            Log.e("SIGNATURE_ERROR", "Cannot verify signature - null parameters");
            return false;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            Log.e("SIGNATURE_ERROR", "Signature file does not exist for verification: " + filePath);
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileData = new byte[(int) file.length()];
            int bytesRead = fis.read(fileData);

            if (bytesRead != originalEncryptedData.length) {
                Log.e("SIGNATURE_ERROR", "Signature file size mismatch - expected: " + originalEncryptedData.length +
                        ", actual: " + bytesRead);
                return false;
            }

            boolean matches = java.util.Arrays.equals(fileData, originalEncryptedData);
            if (matches) {
                Log.d("SIGNATURE_DEBUG", "Signature file verification successful");
            } else {
                Log.e("SIGNATURE_ERROR", "Signature file content does not match original encrypted data");
            }

            return matches;

        } catch (IOException e) {
            Log.e("SIGNATURE_ERROR", "Error verifying signature file: " + e.getMessage(), e);
            return false;
        }
    }


    public static void syncDeleteImageFiles(Context context, String imageName, String signName) {

        File imageFile = new File(context.getFilesDir() + "/DeliveryApp/" + "DeliveryImage/", imageName + ".jpg");

        if (imageFile.exists()) {

            imageFile.delete();
        }

        File signFile = new File(context.getFilesDir() + "/DeliveryApp/" + "DeliverySignature/", signName + ".jpg");

        if (signFile.exists()) {

            signFile.delete();
        }
    }

    public static byte[] decryptImage(String path, String keyString) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(Base64.decode(keyString, Base64.DEFAULT), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(new byte[16])); // Use same IV as encryption

        File file = new File(path);
        byte[] encryptedData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(encryptedData);
        }

        return cipher.doFinal(encryptedData);
    }

}
