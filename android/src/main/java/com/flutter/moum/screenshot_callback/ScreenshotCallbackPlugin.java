package com.flutter.moum.screenshot_callback;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class ScreenshotCallbackPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;

    private String TAG = "ScreenshotCallbackPlugin";
    private Context context;
    private long startListenTime = 0;
    private long screenshotTime = 0;
    private boolean notifyForDescendants = false;
    // 运行在 UI 线程的 Handler, 用于运行监听器回调
    private Handler uiHandler = new Handler(Looper.getMainLooper());
    // 内部存储器内容观察者
    private MediaContentObserver mInternalObserver;
    // 外部存储器内容观察者
    private MediaContentObserver mExternalObserver;
    private static Point sScreenRealSize;
    // 读取媒体数据库时需要读取的列, 其中 WIDTH 和 HEIGHT 字段在 API 16 以后才有
    private static final String[] MEDIA_PROJECTIONS = {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.Images.ImageColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.DATE_MODIFIED,
            MediaStore.Images.ImageColumns.DATE_EXPIRES,
            MediaStore.Images.ImageColumns.WIDTH,
            MediaStore.Images.ImageColumns.HEIGHT,
    };
    /**
     * 媒体查询语句有问题：
     * 出错了 java.lang.IllegalArgumentException: Invalid token limit
     *
     * 在android 11中，添加了一个约束以不允许在排序值中使用LIMIT。
     * 您需要将查询与包参数一起使用。例如
     * https://stackoverflow.com/questions/10390577/limiting-number-of-rows-in-a-contentresolver-query-function/62891878#62891878
     *
     */
    private static final String SORT_ORDER = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
    // 截屏依据中的路径判断关键字
    private static final String[] KEYWORDS = {
            "screenshots", "screen_shots", "screen-shots", "screen shots",
            "screenshot", "screen_shot", "screen-shot", "screen shot", "screencapture",
            "screen_capture", "screen-capture", "screen capture", "screencap", "screen_cap",
            "screen-cap", "screen cap", "截图", "截屏", "截圖", "截屏"
    };
    // 已回调过的路径
    private final static List<String> sHasCallbackPaths = new ArrayList<>();

    /**
     * 获取屏幕分辨率
     */
    private Point getRealScreenSize() {
        Point screenSize = new Point();
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        assert windowManager != null;
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        screenSize.set(displayMetrics.widthPixels, displayMetrics.heightPixels);
        return screenSize;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        channel = new MethodChannel(binding.getBinaryMessenger(), "flutter.moum/screenshot_callback");
        channel.setMethodCallHandler(this);

        this.context = binding.getApplicationContext();
        // 获取屏幕真实的分辨率
        if (sScreenRealSize == null) {
            sScreenRealSize = getRealScreenSize();
            Log.d(TAG, "Screen Real Size: " + sScreenRealSize.x + " * " + sScreenRealSize.y);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("initialize")) {
            startListen();
            result.success("initialize");
        } else if (call.method.equals("dispose")) {
            stopListen();
            result.success("dispose");
        } else {
            result.notImplemented();
        }
    }

    private void assertInMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            StackTraceElement[] elements = Thread.currentThread().getStackTrace();
            String methodMsg = null;
            if (elements.length >= 4) {
                methodMsg = elements[3].toString();
            }
            throw new IllegalStateException("Call the method must be in main thread: " + methodMsg);
        }
    }

    /**
     * 启动监听
     */
    private void startListen() {
        assertInMainThread();
        // 记录开始监听的时间戳
        startListenTime = System.currentTimeMillis();
        // 创建内容观察者
        mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, uiHandler);
        mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, uiHandler);

        // 假设UriMatcher 里注册的Uri共有一下类型：
        // 1 、content://com.qin.cb/student (学生)
        // 2 、content://com.qin.cb/student/#
        // 3、 content://com.qin.cb/student/schoolchild(小学生，派生的Uri)
        // 假设我们当前需要观察的Uri为content://com.qin.cb/student，如果发生数据变化的Uri为
        // content://com.qin.cb/student/schoolchild ，当notifyForDescendants为 false，那么该ContentObserver会监听不到，
        // 但是当notifyForDescendants 为true，能捕捉该Uri的数据库变化。
        if (Build.VERSION.SDK_INT >= 29) notifyForDescendants = true;

        // 注册内容观察者
        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                notifyForDescendants,
                mInternalObserver
        );
        context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                notifyForDescendants,
                mExternalObserver
        );

        Log.d(TAG, "startListen");
    }

    /**
     * 停止监听
     */
    private void stopListen() {
        assertInMainThread();

        // 注销内容观察者
        if (mInternalObserver != null) {
            try {
                context.getContentResolver().unregisterContentObserver(mInternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mInternalObserver = null;
        }
        if (mExternalObserver != null) {
            try {
                context.getContentResolver().unregisterContentObserver(mExternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mExternalObserver = null;
        }

        // 清空数据
        startListenTime = 0;
        sHasCallbackPaths.clear();

        Log.d(TAG, "stopListen");
    }

    /**
     * 处理媒体数据库的内容改变
     */
    private void handleMediaContentChange(Uri contentUri) {
        Cursor cursor = null;
        try {
            // 数据改变时查询数据库中最后加入的一条数据
            cursor = context.getContentResolver().query(
                    contentUri, MEDIA_PROJECTIONS, null, null, SORT_ORDER
            );

            // 获取不到数据的处理
            if (cursor == null) {
                Log.e(TAG, "Deviant logic.");
                return;
            }
            if (!cursor.moveToFirst()) {
                Log.d(TAG, "Cursor no data.");
                return;
            }

            // 获取各列的索引
            int dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_TAKEN);
            int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_ADDED);
            int dateModifyIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_MODIFIED);
            int dateExpireIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATE_EXPIRES);
            int widthIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.WIDTH);
            int heightIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.HEIGHT);
            // 获取行数据
            String data = cursor.getString(dataIndex);
            long dateTaken = cursor.getLong(dateTakenIndex);
            long dateAdded = cursor.getLong(dateAddedIndex);
            long dateModify = cursor.getLong(dateModifyIndex);
            long dateExpire = cursor.getLong(dateExpireIndex);
            int width = cursor.getInt(widthIndex);
            int height = cursor.getInt(heightIndex);

            Log.d(TAG, "ScreenShot: dateTaken = " + dateTaken + "; dateAdded = " + dateAdded +
                    "; dateModify = " + dateModify + "; dateExpire = "+ dateExpire);
            // 处理获取到的第一行数据
            handleMediaRowData(data, dateTaken, width, height);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }
    }

    /**
     * 处理获取到的一行数据
     */
    private void handleMediaRowData(String data, long dateTaken, int width, int height) {
        if (checkScreenShot(data, dateTaken, width, height)) {
            Log.d(TAG, "ScreenShot: path = " + data + "; size = " + width + " * " + height
                    + "; date = " + dateTaken);
            if (!checkCallback(data)) {
                Log.d(TAG, "ScreenShot checkCallback and post");
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        channel.invokeMethod("onCallback", null);
                    }
                });
            }
        } else {
            // 如果在观察区间媒体数据库有数据改变，又不符合截屏规则，则输出到 log 待分析
            Log.w(TAG, "Media content changed, but not screenshot: path = " + data
                    + "; size = " + width + " * " + height + "; date = " + dateTaken);
        }
    }

    /**
     * 判断是否已回调过, 某些手机ROM截屏一次会发出多次内容改变的通知; <br/>
     * 删除一个图片也会发通知, 同时防止删除图片时误将上一张符合截屏规则的图片当做是当前截屏.
     */
    private boolean checkCallback(String imagePath) {
//        if (sHasCallbackPaths.contains(imagePath)) {
//            Log.d(TAG, "ScreenShot: imgPath has done"
//                    + "; imagePath = " + imagePath);
//            return true;
//        }
//        // 大概缓存15~20条记录便可
//        if (sHasCallbackPaths.size() >= 20) {
//            sHasCallbackPaths.subList(0, 5).clear();
//        }
//        sHasCallbackPaths.add(imagePath);
        return false;
    }

    /**
     * 判断指定的数据行是否符合截屏条件
     */
    private boolean checkScreenShot(String data, long dateTaken, int width, int height) {
        Log.d(TAG, "===>>> checkScreenShot 0");
        // 判断依据一: 插入内容时间差
        // 如果插入内容时间差小于3秒, 则认为当前没有截屏，因为部分机型会多次触发
        if (System.currentTimeMillis() - screenshotTime < 2000) {
            Log.d(TAG, "===>>> checkScreenShot 0 ======== " + System.currentTimeMillis() + " === " + screenshotTime);
            screenshotTime = System.currentTimeMillis();
            return false;
        }
        screenshotTime = System.currentTimeMillis();

        Log.d(TAG, "===>>> checkScreenShot 1  dateTaken: " + dateTaken);
        // 判断依据二: 时间判断
        // 如果加入数据库的时间在开始监听之前, 或者与当前时间相差大于5s, 则认为当前没有截屏
        // 某些情况下时间会返回0(Android Q)
//        if (dateTaken < startListenTime || (System.currentTimeMillis() - dateTaken) > 60 * 1000) { //
//            Log.d(TAG, "===>>> checkScreenShot 1 dateTaken: " + dateTaken + " == startListenTime: " +
//                    startListenTime + " === " + (System.currentTimeMillis() - dateTaken));
//            if (dateTaken != 0) return false;
//        }

        Log.d(TAG, "===>>> checkScreenShot 2");
        // 判断依据三: 尺寸判断
        // 如果图片尺寸超出屏幕, 则认为当前没有截屏，高度误差范围 0 - 400
        if (sScreenRealSize != null) {
            if (!((width <= sScreenRealSize.x && height <= sScreenRealSize.y + 400)
                    || (height <= sScreenRealSize.x && width <= sScreenRealSize.y + 400))) {
                return false;
            }
        }

        Log.d(TAG, "===>>> checkScreenShot 3");
        // 判断依据四: 路径判断
        if (TextUtils.isEmpty(data)) return false;
        data = data.toLowerCase();
        // 判断图片路径是否含有指定的关键字之一, 如果有, 则认为当前截屏了
        for (String keyWork : KEYWORDS) {
            Log.d(TAG, "===>>> checkScreenShot ok");
            if (data.contains(keyWork)) return true;
        }

        Log.d(TAG, "===>>> checkScreenShot 4");
        return false;
    }

    /**
     * 媒体内容观察者(观察媒体数据库的改变)
     */
    private class MediaContentObserver extends ContentObserver {

        private Uri mContentUri;

        public MediaContentObserver(Uri contentUri, Handler handler) {
            super(handler);
            mContentUri = contentUri;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            Log.d(TAG, "===>>> checkScreenShot onChange: " + selfChange);
            handleMediaContentChange(mContentUri);
        }
    }
}