package com.antigravity.photoframe;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";
    private PhotoFrameServer server;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 全方位重力感应自适应旋转（横放就横屏，竖放就竖屏，倒过来也自动摆正）
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        // 2. 硬件级常亮锁定：彻底杜绝安卓平板休眠或自动黑屏
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 3. 隐藏系统状态栏（时间、电池、信号）和虚拟导航键，进入100%纯净沉浸式艺术相框模式
        hideSystemUI();

        // 4. 启动本地独立自愈微服务
        try {
            server = new PhotoFrameServer(this);
            server.start();
            Log.i(TAG, "本地独立相框微服务已在端口 " + PhotoFrameServer.PORT + " 启动！");
            copyInitialMediaIfEmpty(server.getMediaDir());
        } catch (Exception e) {
            Log.e(TAG, "启动本地相框服务失败", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemUI();
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            // 隐藏状态栏（时间、信号、电量）和导航栏（返回键、主页键）
            controller.hide(WindowInsetsCompat.Type.systemBars());
            // 粘性沉浸：若从屏幕边缘呼出，2秒后自动再次隐形
            controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }

        // 双重保险兼容老款安卓系统
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        } catch (Exception ignored) {}
    }

    private void copyInitialMediaIfEmpty(File mediaDir) {
        File[] existing = mediaDir.listFiles();
        if (existing != null && existing.length > 0) {
            return; // 已经有照片，不再重复初始化
        }

        // 从打包的 Assets 中提取预置精美壁纸，保证首次全新安装立刻能轮播
        String[] samples = new String[]{"海边温馨小屋.jpg", "雪山夕阳倒影.jpg"};
        for (String name : samples) {
            try {
                InputStream in = getAssets().open("public/media/" + name);
                File dst = new File(mediaDir, name);
                try (OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                Log.i(TAG, "成功注入预置壁纸: " + name);
            } catch (Exception e) {
                // 如果找不到资源则跳过
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }
}
