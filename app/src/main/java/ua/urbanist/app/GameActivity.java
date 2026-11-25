package ua.urbanist.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Екран гри: WebView з тією самою сторінкою, що відкривається в браузері. */
public class GameActivity extends Activity {
    private WebView web;
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        url = getIntent().getStringExtra("url");
        if (url == null) url = "http://127.0.0.1:8000/";

        web = new WebView(this);
        web.setBackgroundColor(0xFF0A1018);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // потрібно для збереження сесії гравця (localStorage)
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(url);
        }
    }

    private void showError() {
        String html = "<html><body style='background:#0a1018;color:#e4ecf8;font-family:sans-serif;text-align:center;padding:40px 20px'>"
                + "<h2>Не вдалося підключитися</h2>"
                + "<p>Перевірте, що телефон у тій самій Wi-Fi мережі, що й господар гри, і що адреса правильна:</p>"
                + "<p style='color:#7fd6ff'>" + url + "</p>"
                + "<p><a style='color:#fff;background:#1c5aa0;padding:12px 20px;border-radius:10px;text-decoration:none' href='" + url + "'>Спробувати ще раз</a></p>"
                + "</body></html>";
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Вийти з гри?")
                .setPositiveButton("Вийти", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("Залишитись", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
