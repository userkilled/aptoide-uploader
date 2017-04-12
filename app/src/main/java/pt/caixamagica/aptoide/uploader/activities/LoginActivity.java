/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 07/04/2016.
 */

package pt.caixamagica.aptoide.uploader.activities;

import android.Manifest;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.facebook.UiLifecycleHelper;
import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.ValidationException;
import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.PendingRequestListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import pt.caixamagica.aptoide.uploader.AptoideUploaderApplication;
import pt.caixamagica.aptoide.uploader.BuildConfig;
import pt.caixamagica.aptoide.uploader.LoginFragment;
import pt.caixamagica.aptoide.uploader.R;
import pt.caixamagica.aptoide.uploader.UploaderUtils;
import pt.caixamagica.aptoide.uploader.components.callbacks.login.LoginActivityCallback;
import pt.caixamagica.aptoide.uploader.dialog.RepoCreatorDialog;
import pt.caixamagica.aptoide.uploader.model.UserInfo;
import pt.caixamagica.aptoide.uploader.retrofit.LoginErrorException;
import pt.caixamagica.aptoide.uploader.retrofit.RetrofitSpiceServiceUploader;
import pt.caixamagica.aptoide.uploader.retrofit.request.OAuth2AuthenticationRequest;
import pt.caixamagica.aptoide.uploader.retrofit.request.UserCredentialsRequest;
import pt.caixamagica.aptoide.uploader.webservices.json.OAuth;
import pt.caixamagica.aptoide.uploader.webservices.json.UserCredentialsJson;

//public class MainActivity extends ActionBarActivity {
public class LoginActivity extends AppCompatActivity
    implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    View.OnClickListener, LoginActivityCallback, SplashDialogFragment.OnHeadlineSelectedListener {

  public static final String SHARED_PREFERENCES_FILE = "UploaderPrefs2";
  public static final byte[] SALT = new byte[] {
      -46, 65, 30, -128, -103, -57, 74, -64, 51, 88, -95, -21, 77, -117, -36, -113, -11, 32, -64, 89
  };
  public static final int USER_RECOVERY_AUTH_REQUEST_CODE = 9001;
  private static final int MY_PERMISSIONS_REQUEST = 1;
  private static final int REQUEST_CODE_RESOLVE_ERR = 9000;
  final long DEFAULT_CACHE_TIME = DurationInMillis.ONE_SECOND * 5;
  public UiLifecycleHelper uiLifecycleHelper;

  SpiceManager spiceManager = new SpiceManager(RetrofitSpiceServiceUploader.class);

  boolean dismissSplash = false;
  //    OAuth2AuthenticationRequest oAuth2AuthenticationRequest;
  OAuth2AuthenticationRequest checkUserCredentialsRequest;
  private SplashDialogFragment splashDialogFragment = new SplashDialogFragment();
  /* Client used to interact with Google APIs. */
  @Getter private GoogleApiClient mGoogleApiClient;
  /* A flag indicating that a PendingIntent is in progress and prevents
   * us from starting further intents.
   */
  private boolean mIntentInProgress;
  /* Store the connection result from onConnectionFailed callbacks so that we can
   * resolve them when the user clicks sign-in.
   */
  private ConnectionResult mConnectionResult;
  private Fragment mContent;
  private UserInfo userInfo;
  private UserCredentialsJson userCredentials;

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    // Google Plus API
    if (requestCode == REQUEST_CODE_RESOLVE_ERR && resultCode == RESULT_OK) {
      mConnectionResult = null;
      mGoogleApiClient.connect();
    } else {
      if (requestCode == USER_RECOVERY_AUTH_REQUEST_CODE && resultCode == RESULT_OK
          || requestCode == 90 && resultCode == RESULT_OK) {

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
          getGoogleToken();
        } else {
          mGoogleApiClient.connect();
        }
      }
    }
  }

  @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      spiceManager.shouldStop();
      spiceManager.start(this);
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override protected void onPause() {
    super.onPause();
  }

  @Override protected void onResume() {
    super.onResume();

    if (dismissSplash && splashDialogFragment != null && splashDialogFragment.isAdded()) {
      dismissSplash = false;
      checkStoredCredentialsCallback();
      splashDialogFragment.dismiss();
      splashDialogFragment = null;
    }
  }

  @Override protected void onStart() {
    super.onStart();

    spiceManager.start(this);
    UploaderUtils.checkSpiceManagerPendingContent(spiceManager, "loginActivity",
        new OAuthPendingRequestListener(), OAuth.class);
    UploaderUtils.checkSpiceManagerPendingContent(spiceManager, "getUserInfo",
        new UserCredentialsPendingRequestListener(), UserCredentialsJson.class);
  }

  @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {

    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST: {

        dismissSplash = true;
      }
      default:
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  @Override public void checkStoredCredentialsCallback() {

    UserCredentialsJson userCredentialsJson = getStoredUserCredentials();
    if (userCredentialsJson != null) {
      switchToAppViewFragment(userCredentialsJson);
    } else {
      setContentView(R.layout.activity_main);
      setSupportActionBar((Toolbar) findViewById(R.id.my_awesome_toolbar));

      mContent = new LoginFragment();

      getSupportFragmentManager().beginTransaction()
          .replace(R.id.container, mContent, "loginFragment")
          .commit();
    }

    AptoideUploaderApplication.firstLaunchApagar = false;
  }

  private UserCredentialsJson getStoredUserCredentials() {

    AccountManager accountManager = AccountManager.get(this);

    SharedPreferences sharedpreferences =
        this.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);

    if (sharedpreferences != null && sharedpreferences.getAll().size() > 0) {
      String deviceId =
          Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

      AESObfuscator aesObfuscator = new AESObfuscator(SALT, this.getPackageName(), deviceId);

      try {
        String token = aesObfuscator.unobfuscate(sharedpreferences.getString("token", ""), "token");
        String refreshToken =
            aesObfuscator.unobfuscate(sharedpreferences.getString("refreshToken", ""),
                "refreshToken");
        String repo = aesObfuscator.unobfuscate(sharedpreferences.getString("repo", ""), "repo");

        UserCredentialsJson userCredentialsJson = new UserCredentialsJson();
        userCredentialsJson.setToken(token);
        userCredentialsJson.setRefreshToken(refreshToken);
        userCredentialsJson.setRepo(repo);
        return userCredentialsJson;
      } catch (ValidationException e) {
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (!AptoideUploaderApplication.isForcedLogout()
        && accountManager.getAccountsByType("cm.aptoide.pt").length != 0) {

      try {
        String URL = "content://cm.aptoide.pt.StubProvider";
        Uri token_uri = Uri.parse(URL + "/token");
        Uri refresh_token_uri = Uri.parse(URL + "/refreshToken");
        Uri repo_uri = Uri.parse(URL + "/repo");

        Cursor c1 = getContentResolver().query(token_uri, null, null, null, null);
        Cursor c2 = getContentResolver().query(refresh_token_uri, null, null, null, null);
        Cursor c3 = getContentResolver().query(repo_uri, null, null, null, null);

        if (c1 != null && c2 != null && c3 != null) {

          c1.moveToFirst();
          c2.moveToFirst();
          c3.moveToFirst();

          UserCredentialsJson userCredentialsJson = new UserCredentialsJson();
          userCredentialsJson.setToken(c1.getString(c1.getColumnIndex("userToken")));
          userCredentialsJson.setRefreshToken(c2.getString(c2.getColumnIndex("userRefreshToken")));
          userCredentialsJson.setRepo(c3.getString(c3.getColumnIndex("userRepo")));

          storeToken(userCredentialsJson);

          c1.close();
          c2.close();
          c3.close();

          return userCredentialsJson;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return null;
  }

  private void switchToAppViewFragment(UserCredentialsJson userCredentialsJson) {
    finish();

    Bundle bundle = new Bundle();
    bundle.putSerializable("userCredentialsJson", userCredentialsJson);

    Intent intent = new Intent(this, AppsListActivity.class);
    intent.putExtras(bundle);
    startActivity(intent);
  }

  private void storeToken(UserCredentialsJson userCredentialsJson) {

    // Try to use more dgradleata here. ANDROID_ID is a single point of attack.
    String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

    AESObfuscator aesObfuscator = new AESObfuscator(SALT, getPackageName(), deviceId);

    SharedPreferences sharedpreferences =
        getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedpreferences.edit();

    editor.putString("token", aesObfuscator.obfuscate(userCredentialsJson.getToken(), "token"));
    editor.putString("refreshToken",
        aesObfuscator.obfuscate(userCredentialsJson.getRefreshToken(), "refreshToken"));
    editor.putString("repo", aesObfuscator.obfuscate(userCredentialsJson.getRepo(), "repo"));

    editor.commit();
  }

  private void getGoogleToken() {

    HandlerThread thread = new HandlerThread("name");
    thread.start();
    Handler handler = new Handler(thread.getLooper());

    handler.post(new Runnable() {
      @Override public void run() {

        final String serverId = BuildConfig.GMS_SERVER_ID;

        String accountName = Plus.AccountApi.getAccountName(mGoogleApiClient);
        try {
          String token = GoogleAuthUtil.getToken(LoginActivity.this, accountName,
              "oauth2:server:client_id:" + serverId + ":api_scope:" + Scopes.PLUS_LOGIN);
          userInfo = new UserInfo(accountName, null, token, OAuth2AuthenticationRequest.Mode.google,
              accountName, null, null, null, 0);
          submitAuthentication(userInfo);
        } catch (IOException e) {
          e.printStackTrace();
        } catch (UserRecoverableAuthException e) {
          startActivityForResult(e.getIntent(), USER_RECOVERY_AUTH_REQUEST_CODE);
        } catch (GoogleAuthException e) {
          e.printStackTrace();
        }
      }
    });
  }

  @Override public void submitAuthentication(UserInfo userInfo) {
    this.userInfo = userInfo;

    checkUserCredentialsRequest = new OAuth2AuthenticationRequest();

    checkUserCredentialsRequest.bean.setUsername(userInfo.getUsername());
    checkUserCredentialsRequest.bean.setPassword(userInfo.getPassword());
    checkUserCredentialsRequest.bean.setOauthToken(userInfo.getOauthToken());
    // Martelada
    checkUserCredentialsRequest.bean.setAuthMode(userInfo.getMode());
    checkUserCredentialsRequest.bean.setOauthUserName(userInfo.getNameForGoogle());

    checkUserCredentialsRequest.bean.setOauthCreateRepo(Integer.toString(userInfo.getCreateRepo()));
    checkUserCredentialsRequest.bean.setRepo(userInfo.getRepo());
    checkUserCredentialsRequest.bean.setPrivacy_user(userInfo.getPrivacyUsername());
    checkUserCredentialsRequest.bean.setPrivacy_pass(userInfo.getPrivacyPassword());

    spiceManager.execute(checkUserCredentialsRequest, "loginActivity", DEFAULT_CACHE_TIME,
        new OAuthPendingRequestListener());

    UploaderUtils.pushLoadingFragment(this, R.id.container,
        "Logging in as " + userInfo.getUsername());
  }

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    checkPermissions();

    if (savedInstanceState != null) {
      userInfo = (UserInfo) savedInstanceState.getSerializable("userInfo");
    }

    if (isSplashShowState()) {
      if (savedInstanceState == null) {
        splashDialogFragment.show(getSupportFragmentManager(), "splashDialog");
      }
    } else {
      if (savedInstanceState == null) {
        checkStoredCredentialsCallback();
      } else {
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.my_awesome_toolbar));
      }
    }

    // Google
    mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .addApi(Plus.API)
        .addScope(Plus.SCOPE_PLUS_LOGIN)
        .build();
  }

  @Override protected void onStop() {
    spiceManager.shouldStop();
    super.onStop();

    if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
      mGoogleApiClient.disconnect();
    }
  }

  @Override protected void onDestroy() {
    super.onDestroy();
  }

  @Override protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putSerializable("userInfo", userInfo);
  }

  public void checkPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

      List<String> requests = new ArrayList<String>();

      if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        requests.add(Manifest.permission.READ_EXTERNAL_STORAGE);
      }
      if (checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
          != PackageManager.PERMISSION_GRANTED) {
        requests.add(Manifest.permission.GET_ACCOUNTS);
      }

      if (!requests.isEmpty()) {
        requestPermissions(requests.toArray(new String[requests.size()]), MY_PERMISSIONS_REQUEST);
      } else {
        dismissSplash = true;
      }
    } else {
      dismissSplash = true;
    }
  }

  public boolean isSplashShowState() {
    return AptoideUploaderApplication.firstLaunchApagar;
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();

    //noinspection SimplifiableIfStatement
    if (id == R.id.action_settings) {
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override public void onConnected(final Bundle bundle) {

    getGoogleToken();
  }

  @Override public void onConnectionSuspended(int i) {
  }

  @Override public void onConnectionFailed(ConnectionResult result) {
    if (!mIntentInProgress && result.hasResolution()) {
      try {
        mIntentInProgress = true;
        result.startResolutionForResult(this, REQUEST_CODE_RESOLVE_ERR);
      } catch (IntentSender.SendIntentException e) {
        // The intent was canceled before it was sent.  Return to the default
        // state and attempt to connect to get an updated ConnectionResult.
        mIntentInProgress = false;
        mGoogleApiClient.connect();
      }
    }
  }

  @Override public void onClick(View v) {

    mGoogleApiClient.connect();
    if (v.getId() == R.id.sign_in_button && !mGoogleApiClient.isConnected()) {
      if (mConnectionResult == null) {
        mGoogleApiClient.connect();
      } else {
        try {
          mConnectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLVE_ERR);
        } catch (IntentSender.SendIntentException e) {
          // Try connecting again.
          mConnectionResult = null;
          mGoogleApiClient.connect();
        }
      }
    }
  }

  private void getUserInfo(OAuth oAuth) {
    UserCredentialsRequest request = new UserCredentialsRequest();
    request.setToken(oAuth.getAccess_token());
    storeToken(oAuth);

    spiceManager.execute(request, "getUserInfo", DEFAULT_CACHE_TIME,
        new UserCredentialsPendingRequestListener());
  }

  private void storeToken(OAuth oAuth) {

    // Try to use more dgradleata here. ANDROID_ID is a single point of attack.
    String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

    AESObfuscator aesObfuscator = new AESObfuscator(SALT, getPackageName(), deviceId);

    SharedPreferences sharedpreferences =
        getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedpreferences.edit();

    editor.putString("token", aesObfuscator.obfuscate(oAuth.getAccess_token(), "token"));
    editor.putString("refreshToken",
        aesObfuscator.obfuscate(oAuth.getRefreshToken(), "refreshToken"));

    editor.commit();
  }

  private void storeRepo(UserCredentialsJson userCredentialsJson) {

    // Try to use more dgradleata here. ANDROID_ID is a single point of attack.
    String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

    AESObfuscator aesObfuscator = new AESObfuscator(SALT, getPackageName(), deviceId);

    SharedPreferences sharedpreferences =
        getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedpreferences.edit();

    editor.putString("repo", aesObfuscator.obfuscate(userCredentialsJson.getRepo(), "repo"));

    editor.commit();
  }

  public class OAuthPendingRequestListener implements PendingRequestListener<OAuth> {

    @Override public void onRequestFailure(SpiceException spiceException) {
      if (spiceException.getCause() instanceof LoginErrorException) {
        Toast.makeText(LoginActivity.this, R.string.loginFail, Toast.LENGTH_SHORT).show();
      }
      UploaderUtils.popLoadingFragment(LoginActivity.this);
    }

    @Override public void onRequestNotFound() {
    }

    @Override public void onRequestSuccess(final OAuth oAuth) {
      if (oAuth == null) {
        return;
      }

      // Caso o login seja efectuado com sucesso.
      // Isto não devia ser bem assim, mas enfim..
      if (!"FAIL".equals(oAuth.getStatus())) {
        getUserInfo(oAuth);
      }/* else if (oAuth.getError().get(0).getCode().equals("AUTH-2")) {
        OAuth2AuthenticationRequest oAuth2AuthenticationRequest = new OAuth2AuthenticationRequest();
				oAuth2AuthenticationRequest.bean.setGrant_type("refresh_token");
				oAuth2AuthenticationRequest.bean.setRefresh_token(userCredentials.getRefreshToken());
				spiceManager.execute(oAuth2AuthenticationRequest, new RequestListener<OAuth>() {
					@Override
					public void onRequestFailure(SpiceException spiceException) {

					}

					@Override
					public void onRequestSuccess(OAuth oAuth) {
						userCredentials.setToken(oAuth.getAccess_token());
					}
				});
			}*/
      // Caso o login seja enviado em branco, cai aqui.
      else {
        UploaderUtils.popLoadingFragment(LoginActivity.this);
        Toast.makeText(LoginActivity.this, R.string.loginFail, Toast.LENGTH_SHORT).show();
      }

      spiceManager.removeAllDataFromCache();
    }
  }

  public class UserCredentialsPendingRequestListener
      implements PendingRequestListener<UserCredentialsJson> {

    @Override public void onRequestFailure(SpiceException spiceException) {
      UploaderUtils.popLoadingFragment(LoginActivity.this);
    }

    @Override public void onRequestSuccess(UserCredentialsJson userCredentialsJson) {

      if (userCredentialsJson == null) {
        UploaderUtils.popLoadingFragment(LoginActivity.this);
        return;
      }

      if (userCredentialsJson.getRepo() == null) {
        RepoCreatorDialog.showRepoCreatorDialog(LoginActivity.this, userInfo);
        Toast.makeText(LoginActivity.this, R.string.no_store_error, Toast.LENGTH_LONG)
            .show();
        UploaderUtils.popLoadingFragment(LoginActivity.this);
        return;
      }
      //storeToken(userCredentialsJson);
      storeRepo(userCredentialsJson);
      switchToAppViewFragment(userCredentialsJson);

      spiceManager.removeAllDataFromCache();
    }

    @Override public void onRequestNotFound() {
    }
  }
}
