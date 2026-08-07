package com.sirius.unlockbrightness;

import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * 拒绝强制亮度（LibXposed API 102 原生实现）。
 *
 * <p>拦截应用设置窗口亮度（{@code WindowManager.LayoutParams.screenBrightness}）的行为，
 * 在窗口属性被提交之后强制把该值恢复为 {@code -1.0f}（跟随系统亮度），
 * 阻止应用把窗口亮度强制设为 {@code 1.0f} 等最高亮度。</p>
 *
 * <p>两个 Hook 目标（{@link Window} 与 {@link WindowManager.LayoutParams}）
 * 都是 Android Framework 类，因此直接使用其 Class 对象，无需借助应用 ClassLoader 查找。</p>
 */
public final class Main extends XposedModule {

    private static final String LOG_TAG = "UnlockBrightness";

    /** Window.setAttributes Hook 的唯一标识，便于调试与热重载管理。 */
    private static final String WINDOW_HOOK_ID = "unlock_brightness_set_attributes";

    /** LayoutParams.copyFrom Hook 的唯一标识，便于调试与热重载管理。 */
    private static final String COPY_FROM_HOOK_ID = "unlock_brightness_copy_from";

    /** 表示窗口亮度跟随系统亮度的值。 */
    private static final float SYSTEM_DEFAULT_BRIGHTNESS = -1.0f;

    /** 两个 Hook 各自独立记录安装状态，单个失败后后续回调仍会重试。 */
    private boolean windowHookInstalled;
    private boolean copyFromHookInstalled;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String packageName = param.getPackageName();

        // 包过滤逻辑，与原有模块完全一致。
        if ("android".equals(packageName)) {
            return;
        }

        if (packageName.startsWith("com.highcapable")) {
            return;
        }

        if (packageName.startsWith("com.fankes")) {
            return;
        }

        // 过滤通过后安装 Hook。
        installHooks();
    }

    /**
     * 安装两个 Hook。
     *
     * <p>synchronized 防止 LibXposed 并发回调导致重复安装；
     * 两个 Hook 分别安装并各自记录状态，其中一个失败不影响另一个，
     * 且失败的 Hook 会在后续 onPackageLoaded() 回调中继续重试，避免半成功状态。</p>
     */
    private synchronized void installHooks() {
        if (!windowHookInstalled) {
            installWindowSetAttributesHook();
        }
        if (!copyFromHookInstalled) {
            installLayoutParamsCopyFromHook();
        }
    }

    /**
     * Hook 点 A：{@link Window#setAttributes(WindowManager.LayoutParams)}。
     *
     * <p>afterHook 语义：先执行原方法，再修改第一个参数
     * （{@code WindowManager.LayoutParams.screenBrightness} 为 {@code -1.0f}），最后返回原结果。</p>
     */
    private void installWindowSetAttributesHook() {
        try {
            Method method = Window.class.getDeclaredMethod(
                    "setAttributes",
                    WindowManager.LayoutParams.class);
            hook(method)
                    .setId(WINDOW_HOOK_ID)
                    .intercept(chain -> {
                Object result = chain.proceed();

                resetBrightness(chain.getArg(0));

                return result;
            });
            windowHookInstalled = true;
        } catch (NoSuchMethodException e) {
            logHookError("Failed to find Window.setAttributes", e);
        }
    }

    /**
     * Hook 点 B：{@link WindowManager.LayoutParams#copyFrom(WindowManager.LayoutParams)}。
     *
     * <p>afterHook 语义：先执行原方法，再修改第一个参数（copyFrom 的源参数）的
     * {@code screenBrightness} 为 {@code -1.0f}，最后返回原结果。
     * 注意此处严格复刻原模块行为，修改的是 {@code chain.getArg(0)}，不是 thisObject。</p>
     */
    private void installLayoutParamsCopyFromHook() {
        try {
            Method method = WindowManager.LayoutParams.class.getDeclaredMethod(
                    "copyFrom",
                    WindowManager.LayoutParams.class);
            hook(method)
                    .setId(COPY_FROM_HOOK_ID)
                    .intercept(chain -> {
                Object result = chain.proceed();

                resetBrightness(chain.getArg(0));

                return result;
            });
            copyFromHookInstalled = true;
        } catch (NoSuchMethodException e) {
            logHookError("Failed to find WindowManager.LayoutParams.copyFrom", e);
        }
    }

    /**
     * 将窗口亮度参数强制恢复为 {@code -1.0f}（跟随系统亮度）。
     *
     * <p>修改的始终是 {@code chain.getArg(0)}（第一个参数），与原有模块语义一致：
     * Window.setAttributes 修改提交的窗口属性；copyFrom 修改源参数。</p>
     */
    private void resetBrightness(Object argument) {
        if (argument instanceof WindowManager.LayoutParams) {
            ((WindowManager.LayoutParams) argument).screenBrightness = SYSTEM_DEFAULT_BRIGHTNESS;
        }
    }

    /**
     * 记录 Hook 安装失败日志，避免单个 Hook 查找失败导致目标应用崩溃。
     */
    private void logHookError(String message, Throwable throwable) {
        log(Log.ERROR, LOG_TAG, message, throwable);
    }
}
