package com.shizy.bookreader;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

	private static final String TAG = MainActivity.class.getSimpleName();

	private static final String HOME = "https://www.baidu.com";
	private static final String SEARCH = "http://m.baidu.com/s?word=";
	private static final String KEY_LAST_PAGE = "last_page";

	private static final String HTTP = "http://";
	private static final String HTTPS = "https://";

	private TextView.OnEditorActionListener mActionListener = new TextView.OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				String input = mInputEdit.getText().toString().trim();
				if (input.startsWith(HTTP) || input.startsWith(HTTPS)) {
					mWebView.loadUrl(input);
				} else {
					mWebView.loadUrl(SEARCH + Uri.encode(input));
				}
				hideSoftInput();
				return true;
			}
			return false;
		}
	};

	private EditText mInputEdit;
	private ProgressBar mProgressBar;
	private WebView mWebView;
	private CustomChromeClient mWebChromeClient;
	private SharedPreferences mPref;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (mWebChromeClient != null) {
			mWebChromeClient.onFileChooserResult(requestCode, resultCode, data);
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		initView();
		initData();
	}

	@Override
	protected void onResume() {
		super.onResume();
		mWebView.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
		mWebView.onPause();
	}

	@Override
	protected void onDestroy() {
		try {
			// 先清空，再删除
			mWebView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
			mWebView.clearHistory();

			ViewGroup parent = (ViewGroup) mWebView.getParent();
			parent.removeView(mWebView);
			mWebView.destroy();
			mWebView = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		if (mWebView.canGoBack()) {
			mWebView.goBack();
			return;
		}
		super.onBackPressed();
	}

	private void initView() {
		mInputEdit = findViewById(R.id.edit_input);
		mInputEdit.setOnEditorActionListener(mActionListener);
		mProgressBar = findViewById(R.id.progress);
		initWebView();
	}

	private void initWebView() {
		mWebView = findViewById(R.id.webview);
		WebSettings settings = mWebView.getSettings();
		settings.setDatabaseEnabled(true);
		settings.setAppCacheEnabled(true);
		settings.setCacheMode(WebSettings.LOAD_DEFAULT);
		settings.setDomStorageEnabled(true);

		mWebChromeClient = new CustomChromeClient();
		mWebView.setWebChromeClient(mWebChromeClient);

		mWebView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				mInputEdit.setText(url);
				mProgressBar.setProgress(0);
				mProgressBar.setVisibility(View.VISIBLE);
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				mProgressBar.setVisibility(View.GONE);
			}

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				mPref.edit().putString(KEY_LAST_PAGE, url).apply();
				view.loadUrl(url);
				return true;
			}

			@Override
			public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
				handler.proceed();
			}
		});
	}

	private void initData() {
		mPref = getSharedPreferences(TAG, MODE_PRIVATE);
		String lastPage = mPref.getString(KEY_LAST_PAGE, null);
		if (lastPage == null) {
			homePage();
		} else {
			mWebView.loadUrl(lastPage);
		}
	}

	private void homePage() {
		mWebView.loadUrl(HOME);
	}

	public void goHome(View view) {
		homePage();
	}

	public void refresh(View view) {
		mWebView.reload();
	}

	public void goBack(View view) {
		if (mWebView.canGoBack()) {
			mWebView.goBack();
		}
	}

	public void goForward(View view) {
		if (mWebView.canGoForward()) {
			mWebView.goForward();
		}
	}

	private void hideSoftInput() {
		InputMethodManager imm = (InputMethodManager) getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null && imm.isActive()) {
			mInputEdit.clearFocus();
			imm.hideSoftInputFromWindow(mInputEdit.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
		}
	}

	private class CustomChromeClient extends WebChromeClient {

		private static final int RC_CHOOSE_FILE = 0x1234;

		private ValueCallback<Uri> mUploadFile;
		private ValueCallback<Uri[]> mUploadMultiFiles;

		@Override
		public void onReceivedTitle(WebView view, String title) {
			Log.d(TAG, "onReceivedTitle: " + title);
		}

		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			super.onProgressChanged(view, newProgress);
			mProgressBar.setProgress(newProgress);
		}

		// 3.0
		public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType) {
			openFileChooser(uploadFile, acceptType, null);
		}

		// 4.1.2
		public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
			mUploadFile = uploadFile;
			openFileChooserActivity(acceptType);
		}

		// 5.0
		@Override
		public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
			mUploadMultiFiles = filePathCallback;
			openFileChooserActivity(fileChooserParams.getAcceptTypes());
			return true;
		}

		private void openFileChooserActivity(String... acceptTypes) {
			String acceptType = "image/*";
			if (acceptTypes != null && acceptTypes.length > 0) {
				acceptType = acceptTypes[0];
			}

			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType(acceptType);
			startActivityForResult(Intent.createChooser(intent, "Chooser"), RC_CHOOSE_FILE);
		}

		private void onFileChooserResult(int requestCode, int resultCode, Intent data) {
			if (requestCode == RC_CHOOSE_FILE) {
				if (mUploadMultiFiles != null) {
					Uri[] uris = null;
					if (data != null && resultCode == Activity.RESULT_OK) {
						String dataString = data.getDataString();
						ClipData clipData = data.getClipData();
						if (clipData != null) {
							uris = new Uri[clipData.getItemCount()];
							for (int i = 0; i < clipData.getItemCount(); i++) {
								uris[i] = clipData.getItemAt(i).getUri();
							}
						}
						if (dataString != null) {
							uris = new Uri[]{Uri.parse(dataString)};
						}
					}
					mUploadMultiFiles.onReceiveValue(uris);
					mUploadMultiFiles = null;
				} else if (mUploadFile != null) {
					Uri uri = (data == null || resultCode != RESULT_OK) ? null : data.getData();
					mUploadFile.onReceiveValue(uri);
					mUploadFile = null;
				}
			}
		}
	}

}
