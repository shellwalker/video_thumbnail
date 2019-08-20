package xyz.justsoft.video_thumbnail;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * VideoThumbnailPlugin
 */
public class VideoThumbnailPlugin implements MethodCallHandler {
    private static String TAG = "ThumbnailPlugin";
    private static final int HIGH_QUALITY_MIN_VAL = 70;

    private final MethodChannel channel;
    private final Activity activity;
    private final Context context;
    private final BinaryMessenger messenger;
    private Result pendingResult;
    private MethodCall methodCall;

    private VideoThumbnailPlugin(Activity activity, Context context, MethodChannel channel, BinaryMessenger messenger) {
        this.activity = activity;
        this.context = context;
        this.channel = channel;
        this.messenger = messenger;
    }


    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "video_thumbnail");
        VideoThumbnailPlugin instance = new VideoThumbnailPlugin(registrar.activity(), registrar.context(), channel, registrar.messenger());
        channel.setMethodCallHandler(instance);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (!setPendingMethodCallAndResult(call, result)) {
            finishWithAlreadyActiveError(result);
            return;
        }

        final Map<String, Object> args = call.arguments();

        try {
            final String video = (String) args.get("video");
            final int format = (int) args.get("format");
            final int maxhow = (int) args.get("maxhow");
            final int quality = (int) args.get("quality");

            if (call.method.equals("file")) {
                final String path = (String) args.get("path");
                ThumbnailFileTask task = new ThumbnailFileTask(this.activity, this.messenger, video, path, format, maxhow, quality);
                task.execute();
                finishWithSuccess();
            } else if (call.method.equals("data")) {
                ThumbnailDataTask task = new ThumbnailDataTask(this.activity, this.messenger, video, format, maxhow, quality);
                task.execute();
                finishWithSuccess();
            } else {
                result.notImplemented();
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.error("exception", e.getMessage(), null);
        }
    }

    private static Bitmap.CompressFormat intToFormat(int format) {
        switch (format) {
            default:
            case 0:
                return Bitmap.CompressFormat.JPEG;
            case 1:
                return Bitmap.CompressFormat.PNG;
            case 2:
                return Bitmap.CompressFormat.WEBP;
        }
    }

    private static String formatExt(int format) {
        switch (format) {
            default:
            case 0:
                return new String("jpg");
            case 1:
                return new String("png");
            case 2:
                return new String("webp");
        }
    }

    private static class ThumbnailDataTask extends AsyncTask<String, Void, ByteBuffer> {
        private WeakReference<Activity> activityReference;
        BinaryMessenger messenger;
        final String vidPath;
        final int format;
        final int maxhow;
        final int quality;

        public ThumbnailDataTask(Activity context, BinaryMessenger messenger, String vidPath, int format, int maxhow, int quality) {
            super();
            this.messenger = messenger;
            this.vidPath = vidPath;
            this.format = format;
            this.maxhow = maxhow;
            this.quality = quality;
            this.activityReference = new WeakReference<>(context);
        }

        @Override
        protected ByteBuffer doInBackground(String... strings) {
            byte[] bytesArray = buildThumbnailData(vidPath, format, maxhow, quality);

            assert bytesArray != null;
            final ByteBuffer buffer = ByteBuffer.allocateDirect(bytesArray.length);
            buffer.put(bytesArray);
            return buffer;
        }

        @Override
        protected void onPostExecute(ByteBuffer buffer) {
            super.onPostExecute(buffer);
            this.messenger.send("video_thumbnail/data/" + this.vidPath, buffer);
            buffer.clear();
        }
    }

    private static class ThumbnailFileTask extends AsyncTask<String, Void, ByteBuffer> {
        private WeakReference<Activity> activityReference;
        BinaryMessenger messenger;
        final String vidPath;
        final String path;
        final int format;
        final int maxhow;
        final int quality;

        public ThumbnailFileTask(Activity context, BinaryMessenger messenger, String vidPath, String path, int format, int maxhow, int quality) {
            super();
            this.messenger = messenger;
            this.vidPath = vidPath;
            this.path = path;
            this.format = format;
            this.maxhow = maxhow;
            this.quality = quality;
            this.activityReference = new WeakReference<>(context);
        }


        @Override
        protected ByteBuffer doInBackground(String... strings) {
            String filePath = buildThumbnailFile(vidPath, path, format, maxhow, quality);
            final ByteBuffer buffer = ByteBuffer.allocateDirect(filePath.length());
            buffer.put(filePath.getBytes());
            return buffer;
        }

        @Override
        protected void onPostExecute(ByteBuffer buffer) {
            super.onPostExecute(buffer);
            this.messenger.send("video_thumbnail/file/" + this.vidPath, buffer);
            buffer.clear();
        }
    }

    private static byte[] buildThumbnailData(String vidPath, int format, int maxhow, int quality) {
        Log.d(TAG, String.format("buildThumbnailData( format:%d, maxhow:%d, quality:%d )", format, maxhow, quality));
        Bitmap bitmap = createVideoThumbnail(vidPath, maxhow);
        if (bitmap == null)
            throw new NullPointerException();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(intToFormat(format), quality, stream);
        bitmap.recycle();
        if (bitmap == null)
            throw new NullPointerException();
        return stream.toByteArray();
    }

    private static String buildThumbnailFile(String vidPath, String path, int format, int maxhow, int quality) {
        Log.d(TAG, String.format("buildThumbnailFile( format:%d, maxhow:%d, quality:%d )", format, maxhow, quality));
        final byte bytes[] = buildThumbnailData(vidPath, format, maxhow, quality);
        final String ext = formatExt(format);
        final int i = vidPath.lastIndexOf(".");
        String fullpath = vidPath.substring(0, i + 1) + ext;

        if (path != null) {
            if (path.endsWith(ext)) {
                fullpath = path;
            } else {
                // try to save to same folder as the vidPath
                final int j = fullpath.lastIndexOf("/");

                if (path.endsWith("/")) {
                    fullpath = path + fullpath.substring(j + 1);
                } else {
                    fullpath = path + fullpath.substring(j);
                }
            }
        }

        try {
            FileOutputStream f = new FileOutputStream(fullpath);
            f.write(bytes);
            f.close();
            Log.d(TAG, String.format("buildThumbnailFile( written:%d )", bytes.length));
        } catch (java.io.IOException e) {
            e.getStackTrace();
            throw new RuntimeException(e);
        }
        return fullpath;
    }

    /**
     * Create a video thumbnail for a video. May return null if the video is corrupt
     * or the format is not supported.
     *
     * @param video      the URI of video
     * @param targetSize max width or height of the thumbnail
     */
    public static Bitmap createVideoThumbnail(String video, int targetSize) {
        Bitmap bitmap = null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            Log.d(TAG, String.format("setDataSource: %s )", video));
            if (video.startsWith("file://") || video.startsWith("/")) {
                retriever.setDataSource(video);
            } else {
                retriever.setDataSource(video, new HashMap<String, String>());
            }
            bitmap = retriever.getFrameAtTime(-1);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }

        if (bitmap == null)
            return null;

        if (targetSize != 0) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int max = Math.max(width, height);
            float scale = (float) targetSize / max;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            Log.d(TAG, String.format("original w:%d, h:%d, scale:%6.4f => %d, %d", width, height, scale, w, h));
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

    private void finishWithSuccess() {
        if (pendingResult != null) {
            pendingResult.success(true);
        }
        clearMethodCallAndResult();
    }

    private void finishWithSuccess(String path) {
        if (pendingResult != null)
            pendingResult.success(path);
        clearMethodCallAndResult();
    }

    private void clearMethodCallAndResult() {
        methodCall = null;
        pendingResult = null;
    }

    private void finishWithAlreadyActiveError(MethodChannel.Result result) {
        if (result != null)
            result.error("already_active", "Image picker is already active", null);
    }

    private boolean setPendingMethodCallAndResult(
            MethodCall methodCall, MethodChannel.Result result) {
        if (pendingResult != null) {
            return false;
        }

        this.methodCall = methodCall;
        pendingResult = result;
        return true;
    }
}
