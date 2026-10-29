package com.nia.haedum;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

/** 해둠: assets/index.html 을 앱 한의 WebView 로 띄움. 데이터는 WebView localStorage 에 저장됨. */
public class MainActivity extends ComponentActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";

    private WebView web;
    private AdView adView;
    private FrameLayout adBox;

    /** 안드로이드 13+ 알림 권한 요청 (주기가 있는 집안일이 생겼을 때 한 번만 물어봄) */
    private final ActivityResultLauncher<String> notifyPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 화면 끝까지 그리고, 시스템 바 영역만큼 안쪽 여백을 줌
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat bars =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(!night);
        bars.setAppearanceLightNavigationBars(!night);

        // 위: 해둠 화면(WebView) / 아래: 광고 배너
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        adBox = new FrameLayout(this);
        adBox.setBackgroundColor(getColor(R.color.bg));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            // 키보드가 올라와 있는 동안은 광고를 숨김 (잘못 눌리는 것 방지)
            boolean keyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
            adBox.setVisibility(keyboard ? View.GONE : View.VISIBLE);
            return WindowInsetsCompat.CONSUMED;
        });

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        web = new WebView(this);
        web.setBackgroundColor(getColor(R.color.bg));
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        // 길게 누르기는 페이지 쪽(편집 시트)에서 처리하므로 기본 텍스트 선택 메뉴는 끔
        web.setOnLongClickListener(v -> true);
        web.setLongClickable(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        // 확대/축소 끄기 (두 번 탭, 두 손가락 확대 모두)
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);

        // 페이지가 집안일 목록을 넘겨주면 주기 초과 알림을 다시 예약함
        web.addJavascriptInterface(new Bridge(), "HaedumNative");

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // 광고 자리가 있으면 페이지 아래 여백을 줄여서 버튼이 엄지 높이에 머물게 함
                view.evaluateJavascript("document.documentElement.classList.add('has-ad')", null);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if (HOST.equals(uri.getHost())) return false;
                // 외부 링크는 브라우저로
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(adBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        setupBanner();

        // 뒤로가기: 편집 시트 닫기 → 기록 화면 닫기 → 앱 종료 순서
        OnBackPressedCallback back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript("window.haedumBack ? window.haedumBack() : false", result -> {
                    if (!"true".equals(result)) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }
        };
        getOnBackPressedDispatcher().addCallback(this, back);

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    /** 하단 배너. 개발 중(디버그 빌드)에는 구글 테스트 광고, 출시 빌드에는 내 광고 단위를 씀 */
    private void setupBanner() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        AdSize size = AdSize.getLargeAnchoredAdaptiveBannerAdSize(this, widthDp);

        // 광고가 늦게 떠도 화면이 들썩이지 않게 자리를 미리 잡아 둠
        adBox.setMinimumHeight(size.getHeightInPixels(this));

        adView = new AdView(this);
        adView.setAdUnitId(BuildConfig.DEBUG
                ? getString(R.string.banner_test_unit_id)
                : getString(R.string.banner_unit_id));
        adView.setAdSize(size);
        adBox.addView(adView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER));

        // 초기화는 백그라운드에서, 광고 요청은 바로 (초기화가 끝나면 SDK가 이어서 불러옴)
        new Thread(() -> MobileAds.initialize(this)).start();
        adView.loadAd(new AdRequest.Builder().build());
    }

    /** JS 에서 window.HaedumNative.sync(json) 으로 호출 */
    private class Bridge {
        @JavascriptInterface
        public void sync(String json) {
            Reminders.save(MainActivity.this, json);
            Reminders.reschedule(MainActivity.this);
            if (Reminders.hasCycle(json)) runOnUiThread(MainActivity.this::askNotifyPermissionOnce);
        }
    }

    private void askNotifyPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = getSharedPreferences(Reminders.PREFS, MODE_PRIVATE);
        if (p.getBoolean("askedNotify", false)) return;
        p.edit().putBoolean("askedNotify", true).apply();
        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (adView != null) adView.resume();
        // 날짜가 바뀐 뒤 앱으로 돌아왔을 때 경과일 다시 계산
        web.evaluateJavascript("window.haedumRefresh && window.haedumRefresh()", null);
    }

    @Override
    protected void onPause() {
        web.onPause();
        if (adView != null) adView.pause();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (adView != null) {
            adView.destroy();
        }
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
