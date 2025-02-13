package com.getcapacitor.community.media;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;


@NativePlugin(
    permissions = {
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    },
    requestCodes = {
        MediaPlugin.REQUEST_SAVE_PHOTO,
        MediaPlugin.REQUEST_CREATE_ALBUM
    }
)
public class MediaPlugin extends Plugin {
    protected static final int REQUEST_SAVE_PHOTO = 1001; // Unique request code
    protected static final int REQUEST_CREATE_ALBUM = 1002; // Unique request code

    // @todo
    @PluginMethod()
    public void getMedias(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod()
    public void getAlbums(PluginCall call) {
        Log.d("DEBUG LOG", "GET ALBUMS");
        if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _getAlbums(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            saveCall(call);
            pluginRequestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, 1986);
        }
    }

    private void _getAlbums(PluginCall call) {
        Log.d("DEBUG LOG", "___GET ALBUMS");

        JSObject response = new JSObject();
        JSArray albums = new JSArray();
        StringBuffer list = new StringBuffer();

        String[] projection = new String[]{MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME};
        Cursor cur = getActivity().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null);

        while (cur.moveToNext()) {
            String albumName = cur.getString((cur.getColumnIndex(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)));
            JSObject album = new JSObject();

            list.append(albumName + "\n");

            album.put("name", albumName);
            albums.put(album);
        }

        response.put("albums", albums);
        Log.d("DEBUG LOG", String.valueOf(response));
        Log.d("DEBUG LOG", "___GET ALBUMS FINISHED");

        call.resolve(response);
    }


    @PluginMethod()
    public void getPhotos(PluginCall call) {
        call.unimplemented();
    }

    @PluginMethod()
    public void createAlbum(PluginCall call) {
        Log.d("DEBUG LOG", "CREATE ALBUM");
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _createAlbum(call);
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            saveCall(call);
            pluginRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_CREATE_ALBUM);
        }
    }

    private void _createAlbum(PluginCall call) {
        Log.d("DEBUG LOG", "___CREATE ALBUM");
        String folderName = call.getString("name");
        String folder;

        if (Build.VERSION.SDK_INT >= 29) {
            folder = getContext().getExternalMediaDirs()[0].getAbsolutePath() + "/" + folderName;
        } else {
            folder = Environment.getExternalStoragePublicDirectory(folderName).toString();
        }

        Log.d("ENV STORAGE", folder);

        File f = new File(folder);

        if (!f.exists()) {
            if (!f.mkdir()) {
                Log.d("DEBUG LOG", "___ERROR ALBUM");
                call.error("Cant create album");
            } else {
                Log.d("DEBUG LOG", "___SUCCESS ALBUM CREATED");
                call.success();
            }
        } else {
            Log.d("DEBUG LOG", "ALBUM ALREADY EXISTS");
            call.success();
        }

    }


    @PluginMethod()
    public void savePhoto(PluginCall call) {
        Log.d("DEBUG LOG", "SAVE PHOTO TO ALBUM");
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _saveMedia(call, "PICTURES");
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            saveCall(call);
            pluginRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_SAVE_PHOTO);
            Log.d("DEBUG LOG", "___SAVE PHOTO TO ALBUM AFTER PERMISSION REQUEST");
        }
    }

    @PluginMethod()
    public void saveVideo(PluginCall call) {
        Log.d("DEBUG LOG", "SAVE VIDEO TO ALBUM");
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _saveMedia(call, "MOVIES");
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            saveCall(call);
            pluginRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1986);
        }
    }


    @PluginMethod()
    public void saveGif(PluginCall call) {
        Log.d("DEBUG LOG", "SAVE GIF TO ALBUM");
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Log.d("DEBUG LOG", "HAS PERMISSION");
            _saveMedia(call, "PICTURES");
        } else {
            Log.d("DEBUG LOG", "NOT ALLOWED");
            saveCall(call);
            pluginRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1986);
        }
    }


    private void _saveMedia(PluginCall call, String destination) {
        String dest;
        if (destination == "MOVIES") {
            dest = Environment.DIRECTORY_MOVIES;
        } else {
            dest = Environment.DIRECTORY_PICTURES;
        }

        Log.d("DEBUG LOG", "___SAVE MEDIA TO ALBUM");
        String inputPath = call.getString("path");
        if (inputPath == null) {
            call.reject("Input file path is required");
            return;
        }

        Uri inputUri = Uri.parse(inputPath);
        boolean isRemoteUrl = "http".equalsIgnoreCase(inputUri.getScheme()) || "https".equalsIgnoreCase(inputUri.getScheme());
        Log.d("isRemoteURL", String.valueOf(isRemoteUrl));

        InputStream is = null;
        String extension = "jpg";

        if (isRemoteUrl) {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(inputPath);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5 * 1000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    call.error("Can't access file!");
                    return;
                }
                is = conn.getInputStream();
                extension = inputPath.substring(inputPath.lastIndexOf('.') + 1).split("\\?")[0].split("#")[0];
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            File inputFile = null;
            try {
                inputFile = new File(inputUri.getPath());
                is = new FileInputStream(inputFile);
                String absolutePath = inputFile.getAbsolutePath();
                extension = absolutePath.substring(absolutePath.lastIndexOf(".") + 1);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Source file not found: " + inputFile + ", error: " + e.getMessage());
            }
        }


        String album = call.getString("album");
        File albumDir = null;
        String albumPath;
        Log.d("SDK BUILD VERSION", String.valueOf(Build.VERSION.SDK_INT));

        if (Build.VERSION.SDK_INT >= 29) {
            albumPath = getContext().getExternalMediaDirs()[0].getAbsolutePath();

        } else {
            albumPath = Environment.getExternalStoragePublicDirectory(dest).getAbsolutePath();
        }

        // Log.d("ENV LOG", String.valueOf(getContext().getExternalMediaDirs()));

        if (album != null) {
            albumDir = new File(albumPath, album);
        } else {
            call.error("album name required");
        }

        Log.d("ENV LOG - ALBUM DIR", String.valueOf(albumDir));

        try {
            File expFile = copyFile(is, albumDir, extension);
            scanPhoto(expFile);

            JSObject result = new JSObject();
            result.put("filePath", expFile.toString());
            call.resolve(result);

        } catch (RuntimeException e) {
            call.reject("RuntimeException occurred", e);
        }

    }

    private File copyFile(InputStream is, File albumDir, String extension) {

        // if destination folder does not exist, create it
        if (!albumDir.exists()) {
            if (!albumDir.mkdir()) {
                throw new RuntimeException("Destination folder does not exist and cannot be created.");
            }
        }

        // generate image file name using current date and time
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
        File newFile = new File(albumDir, "IMG_" + timeStamp + "." + extension);
        Log.d("NEW_FILE", newFile.toString());

        // Write image files
        FileChannel outChannel = null;

        try {
            outChannel = new FileOutputStream(newFile).getChannel();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Copy file not found: " + newFile + ", error: " + e.getMessage());
        }

        try {
            outChannel.transferFrom(Channels.newChannel(is), 0, Long.MAX_VALUE);
        } catch (IOException e) {
            throw new RuntimeException("Error transfering file, error: " + e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.d("SaveImage", "Error closing input stream: " + e.getMessage());
                    // does not harm, do nothing
                }
            }
            if (outChannel != null) {
                try {
                    outChannel.close();
                } catch (IOException e) {
                    Log.d("SaveImage", "Error closing output file channel: " + e.getMessage());
                    // does not harm, do nothing
                }
            }
        }

        return newFile;
    }

    private void scanPhoto(File imageFile) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(imageFile);
        mediaScanIntent.setData(contentUri);
        bridge.getActivity().sendBroadcast(mediaScanIntent);
    }

    @PluginMethod()
    public void hasStoragePermission(PluginCall call) {
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            call.success();
        } else {
            call.error("permission denied WRITE_EXTERNAL_STORAGE");
        }
    }

    @PluginMethod()
    public void requestStoragePermission(PluginCall call) {
        pluginRequestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, 1986);
        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            call.success();
        } else {
            call.error("permission denied");
        }
    }

    @Override
    protected void handleRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.handleRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("requestCode", String.valueOf(requestCode));
        Log.d("permissions", permissions.toString());
        Log.d("grantResults", grantResults.toString());

        PluginCall savedCall = getSavedCall();
        if (savedCall == null) {
            return;
        }

        for(int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                savedCall.error("User denied permission");
                return;
            }
        }
        if (requestCode == REQUEST_SAVE_PHOTO) {
            _saveMedia(savedCall, "PICTURES");
        } else if (requestCode == REQUEST_CREATE_ALBUM) {
            _createAlbum(savedCall);
        }

    }

}
