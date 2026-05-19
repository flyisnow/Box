package com.github.tvbox.osc.util;

import android.os.Handler;
import android.os.Looper;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.util.UUID;

/**
 * 远程控制管理器
 * <p>
 * 负责：
 * 1. 初始化设备 ID（首次启动时生成 UUID 并持久化）
 * 2. 向 Cloudflare Worker 注册设备
 * 3. 每隔 30 秒轮询主控 URL，获取成功后写入本地配置，停止轮询
 */
public class RemoteControlManager {

    private static final long POLL_INTERVAL_MS = 30_000L;

    private static RemoteControlManager instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private RemoteControlManager() {
    }

    public static synchronized RemoteControlManager get() {
        if (instance == null) {
            instance = new RemoteControlManager();
        }
        return instance;
    }

    /** 应用启动后调用，完成设备 ID 初始化、注册和主控 URL 查询 */
    public void init() {
        ensureDeviceId();
        registerDevice();
        if (getRemoteControlUrl().isEmpty()) {
            startPolling();
        }
    }

    /** 返回当前设备 ID */
    public String getDeviceId() {
        return Hawk.get(HawkConfig.DEVICE_ID, "");
    }

    /** 返回已保存的主控 URL（空字符串表示未设置） */
    public String getRemoteControlUrl() {
        return Hawk.get(HawkConfig.REMOTE_CONTROL_URL, "");
    }

    /**
     * 重置设备 ID：生成新 UUID，清除主控 URL，重新注册并开始轮询。
     * 必须在调用前向用户展示确认对话框。
     */
    public void resetDeviceId() {
        stopPolling();
        Hawk.put(HawkConfig.DEVICE_ID, generateUUID());
        Hawk.put(HawkConfig.REMOTE_CONTROL_URL, "");
        registerDevice();
        startPolling();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void ensureDeviceId() {
        if (getDeviceId().isEmpty()) {
            Hawk.put(HawkConfig.DEVICE_ID, generateUUID());
        }
    }

    private String generateUUID() {
        return UUID.randomUUID().toString();
    }

    private String getWorkerBaseUrl() {
        return Hawk.get(HawkConfig.WORKER_BASE_URL, "");
    }

    private void registerDevice() {
        String base = getWorkerBaseUrl();
        if (base.isEmpty()) return;
        String url = base + "/api/register?device_id=" + getDeviceId();
        OkGo.<String>post(url)
                .tag("rc_register")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() != null ? response.body().string() : "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        LOG.i("RemoteControl: device registered");
                    }

                    @Override
                    public void onError(Response<String> response) {
                        LOG.e("RemoteControl: register failed");
                    }
                });
    }

    private void startPolling() {
        if (polling) return;
        polling = true;
        handler.postDelayed(pollRunnable, 0);
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            fetchRemoteControlUrl();
        }
    };

    private void fetchRemoteControlUrl() {
        String base = getWorkerBaseUrl();
        if (base.isEmpty()) {
            scheduleNextPoll();
            return;
        }
        String url = base + "/api/getUrl?device_id=" + getDeviceId();
        OkGo.<String>get(url)
                .tag("rc_getUrl")
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body() != null ? response.body().string() : "";
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            JSONObject json = new JSONObject(response.body());
                            String controlUrl = json.optString("url", "");
                            if (!controlUrl.isEmpty()) {
                                Hawk.put(HawkConfig.REMOTE_CONTROL_URL, controlUrl);
                                stopPolling();
                                LOG.i("RemoteControl: url obtained = " + controlUrl);
                            } else {
                                scheduleNextPoll();
                            }
                        } catch (Exception e) {
                            scheduleNextPoll();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        scheduleNextPoll();
                    }
                });
    }

    private void scheduleNextPoll() {
        if (!polling) return;
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }
}
