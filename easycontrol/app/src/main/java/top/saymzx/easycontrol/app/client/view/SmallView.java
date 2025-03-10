package top.saymzx.easycontrol.app.client.view;

import android.annotation.SuppressLint;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.os.Build;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.saymzx.easycontrol.app.R;
import top.saymzx.easycontrol.app.client.ClientController;
import top.saymzx.easycontrol.app.client.ControlPacket;
import top.saymzx.easycontrol.app.databinding.ModuleSmallViewBinding;
import top.saymzx.easycontrol.app.entity.AppData;
import top.saymzx.easycontrol.app.entity.Device;
import top.saymzx.easycontrol.app.helper.PublicTools;
import top.saymzx.easycontrol.app.helper.ViewTools;

public class SmallView extends ViewOutlineProvider {
  private final Device device;
  private boolean isShow = false;
  private boolean light = true;

  // 悬浮窗
  private final ModuleSmallViewBinding smallView = ModuleSmallViewBinding.inflate(LayoutInflater.from(AppData.applicationContext));
  private final WindowManager.LayoutParams smallViewParams =
    new WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
      LayoutParamsFlagFocus,
      PixelFormat.TRANSLUCENT
    );

  private static final int LayoutParamsFlagFocus = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  private static final int LayoutParamsFlagNoFocus = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

  public SmallView(Device device) {
    this.device = device;
    smallViewParams.gravity = Gravity.START | Gravity.TOP;
    // 设置默认导航栏状态
    setNavBarHide(AppData.setting.getShowNavBarOnConnect());
    // 设置监听控制
    setFloatVideoListener();
    setReSizeListener();
    setBarListener();
    setButtonListener();
    setKeyEvent();
    // 设置圆角
    smallView.body.setOutlineProvider(this);
    smallView.body.setClipToOutline(true);
  }

  public void show() {
    // 初始化
    smallView.barView.setVisibility(View.GONE);
    smallViewParams.x = device.small_x;
    smallViewParams.y = device.small_y;
    updateMaxSize(device.small_length, device.small_length);
    // 显示
    AppData.windowManager.addView(smallView.getRoot(), smallViewParams);
    smallView.textureViewLayout.addView(ClientController.getTextureView(device.uuid), 0);
    ViewTools.viewAnim(smallView.getRoot(), true, 0, PublicTools.dp2px(40f), null);
    isShow = true;
  }

  public void hide() {
    try {
      smallView.textureViewLayout.removeView(ClientController.getTextureView(device.uuid));
      AppData.windowManager.removeView(smallView.getRoot());
      isShow = false;
    } catch (Exception ignored) {
    }
  }

  // 获取默认宽高比，用于修改分辨率使用
  public static float getResolution() {
    return 0.55f;
  }

  // 设置焦点监听
  @SuppressLint("ClickableViewAccessibility")
  private void setFloatVideoListener() {
    boolean smallToMiniOnOutside = AppData.setting.getSmallToMiniOnOutside();
    smallView.getRoot().setOnTouchHandle(event -> {
      if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
        if (smallToMiniOnOutside) ClientController.handleControll(device.uuid, "changeToMini", ByteBuffer.wrap("changeToSmall".getBytes()));
        else if (smallViewParams.flags != LayoutParamsFlagNoFocus) {
          smallView.editText.clearFocus();
          smallViewParams.flags = LayoutParamsFlagNoFocus;
          AppData.windowManager.updateViewLayout(smallView.getRoot(), smallViewParams);
        }
      } else if (smallViewParams.flags != LayoutParamsFlagFocus) {
        smallView.editText.requestFocus();
        smallViewParams.flags = LayoutParamsFlagFocus;
        AppData.windowManager.updateViewLayout(smallView.getRoot(), smallViewParams);
      }
    });
  }

  // 设置上横条监听控制
  @SuppressLint("ClickableViewAccessibility")
  private void setBarListener() {
    AtomicBoolean isFilp = new AtomicBoolean(false);
    AtomicInteger xx = new AtomicInteger();
    AtomicInteger yy = new AtomicInteger();
    AtomicInteger paramsX = new AtomicInteger();
    AtomicInteger paramsY = new AtomicInteger();
    smallView.bar.setOnTouchListener((v, event) -> {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN: {
          xx.set((int) event.getRawX());
          yy.set((int) event.getRawY());
          paramsX.set(smallViewParams.x);
          paramsY.set(smallViewParams.y);
          isFilp.set(false);
          break;
        }
        case MotionEvent.ACTION_MOVE: {
          int x = (int) event.getRawX();
          int y = (int) event.getRawY();
          int flipX = x - xx.get();
          int flipY = y - yy.get();
          // 适配一些机器将点击视作小范围移动(小于4的圆内不做处理)
          if (!isFilp.get()) {
            if (flipX * flipX + flipY * flipY < 16) return true;
            isFilp.set(true);
          }
          // 拖动限制，避免拖到状态栏
          if (y < statusBarHeight + 10) return true;
          // 更新
          updateSite(paramsX.get() + flipX, paramsY.get() + flipY);
          break;
        }
        case MotionEvent.ACTION_UP:
          if (!isFilp.get()) changeBarView();
          break;
      }
      return true;
    });
  }

  // 设置按钮监听
  private void setButtonListener() {
    smallView.buttonRotate.setOnClickListener(v -> ClientController.handleControll(device.uuid, "buttonRotate", null));
    smallView.buttonBack.setOnClickListener(v -> ClientController.handleControll(device.uuid, "buttonBack", null));
    smallView.buttonHome.setOnClickListener(v -> ClientController.handleControll(device.uuid, "buttonHome", null));
    smallView.buttonSwitch.setOnClickListener(v -> ClientController.handleControll(device.uuid, "buttonSwitch", null));
    smallView.buttonNavBar.setOnClickListener(v -> {
      setNavBarHide(smallView.navBar.getVisibility() == View.GONE);
      changeBarView();
    });
    smallView.buttonMini.setOnClickListener(v -> ClientController.handleControll(device.uuid, "changeToMini", null));
    smallView.buttonFull.setOnClickListener(v -> ClientController.handleControll(device.uuid, "changeToFull", null));
    smallView.buttonClose.setOnClickListener(v -> ClientController.handleControll(device.uuid, "close", null));
    smallView.buttonLight.setOnClickListener(v -> {
      light = !light;
      smallView.buttonLight.setImageResource(light ? R.drawable.lightbulb_off : R.drawable.lightbulb);
      ClientController.handleControll(device.uuid, light ? "buttonLight" : "buttonLightOff", null);
      changeBarView();
    });
    smallView.buttonPower.setOnClickListener(v -> {
      ClientController.handleControll(device.uuid, "buttonPower", null);
      changeBarView();
    });
  }

  // 导航栏隐藏
  private void setNavBarHide(boolean isShow) {
    smallView.navBar.setVisibility(isShow ? View.VISIBLE : View.GONE);
    smallView.buttonNavBar.setImageResource(isShow ? R.drawable.not_equal : R.drawable.equals);
  }

  private void changeBarView() {
    boolean toShowView = smallView.barView.getVisibility() == View.GONE;
    ViewTools.viewAnim(smallView.barView, toShowView, 0, PublicTools.dp2px(-40f), (isStart -> {
      if (isStart && toShowView) smallView.barView.setVisibility(View.VISIBLE);
      else if (!isStart && !toShowView) smallView.barView.setVisibility(View.GONE);
    }));
  }

  // 设置悬浮窗大小拖动按钮监听控制
  @SuppressLint("ClickableViewAccessibility")
  private void setReSizeListener() {
    smallView.reSize.setOnTouchListener((v, event) -> {
      if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
        int sizeX = (int) (event.getRawX() - smallViewParams.x);
        int sizeY = (int) (event.getRawY() - smallViewParams.y);
        int length = Math.max(sizeX, sizeY);
        updateMaxSize(length, length);
      }
      return true;
    });
  }

  private void updateSite(int x, int y) {
    device.small_x = x;
    device.small_y = y;
    smallViewParams.x = x;
    smallViewParams.y = y;
    AppData.windowManager.updateViewLayout(smallView.getRoot(), smallViewParams);
  }

  private void updateMaxSize(int w, int h) {
    device.small_length = w;
    ByteBuffer byteBuffer = ByteBuffer.allocate(8);
    byteBuffer.putInt(w);
    byteBuffer.putInt(h);
    byteBuffer.flip();
    ClientController.handleControll(device.uuid, "updateMaxSize", byteBuffer);
  }

  // 设置键盘监听
  private void setKeyEvent() {
    smallView.editText.setInputType(InputType.TYPE_NULL);
    smallView.editText.setOnKeyListener((v, keyCode, event) -> {
      if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
        ClientController.handleControll(device.uuid, "writeByteBuffer", ControlPacket.createKeyEvent(event.getKeyCode(), event.getMetaState()));
        return true;
      }
      return false;
    });
  }

  // 检查画面是否超出
  public void checkSizeAndSite() {
    if (!isShow) return;
    DisplayMetrics screenSize = PublicTools.getScreenSize();
    int screenMaxWidth = screenSize.widthPixels - 50;
    int screenMaxHeight = screenSize.heightPixels - statusBarHeight - 50;
    TextureView textureView = ClientController.getTextureView(device.uuid);
    if (textureView == null) return;
    ViewGroup.LayoutParams textureViewLayoutParams = textureView.getLayoutParams();
    int width = textureViewLayoutParams.width;
    int height = textureViewLayoutParams.height;
    int startX = smallViewParams.x;
    int startY = smallViewParams.y;
    // 检测到大小超出
    if (width > screenMaxWidth + 200 || height > screenMaxHeight + 200) {
      int maxLength = Math.min(screenMaxWidth, screenMaxHeight);
      updateMaxSize(maxLength, maxLength);
      updateSite(50, statusBarHeight);
      return;
    }
    // 检测到位置超出过多
    int halfWidth = (int) (width * 0.5);
    if (startX < -1 * halfWidth) updateSite(-1 * halfWidth + 50, startY);
    if (startX > screenSize.widthPixels - halfWidth) updateSite(screenSize.widthPixels - halfWidth - 50, startY);
    if (startY < statusBarHeight / 2) updateSite(startX, statusBarHeight);
    if (startY > screenSize.heightPixels - 100) updateSite(startX, screenSize.heightPixels - 200);
  }

  @Override
  public void getOutline(View view, Outline outline) {
    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AppData.applicationContext.getResources().getDimension(R.dimen.round));
  }

  private static int statusBarHeight = 0;

  static {
    @SuppressLint("InternalInsetResource") int resourceId = AppData.applicationContext.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      statusBarHeight = AppData.applicationContext.getResources().getDimensionPixelSize(resourceId);
    }
  }

}
