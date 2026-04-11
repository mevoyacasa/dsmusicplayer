package mevoycasa.dsmusic;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_SKIP_AUTO_LOGIN = "extra_skip_auto_login";

    private ApiClient apiClient;
    private TextInputEditText editHost;
    private TextInputEditText editAccount;
    private TextInputEditText editPassword;
    private TextInputLayout layoutAccount;
    private MaterialCheckBox checkRememberAccount;
    private MaterialCheckBox checkRememberPassword;
    private MaterialCheckBox checkForceHttps;
    private MaterialCheckBox checkIgnoreCert;
    private MaterialButton buttonLogin;
    private TextView textStatus;
    private ActivityResultLauncher<Intent> pickCacheLocationLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 设置应用语言
        String language = new ApiClient(this).getAppLanguage();
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.locale = locale;
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        apiClient = new ApiClient(this);
        editHost = findViewById(R.id.editHost);
        editAccount = findViewById(R.id.editAccount);
        editPassword = findViewById(R.id.editPassword);
        layoutAccount = findViewById(R.id.layoutAccount);
        checkRememberAccount = findViewById(R.id.checkRememberAccount);
        checkRememberPassword = findViewById(R.id.checkRememberPassword);
        checkForceHttps = findViewById(R.id.checkForceHttps);
        checkIgnoreCert = findViewById(R.id.checkIgnoreCert);
        buttonLogin = findViewById(R.id.buttonLogin);
        textStatus = findViewById(R.id.textStatus);
        pickCacheLocationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handlePickCacheLocationResult(result.getResultCode(), result.getData())
        );

        buttonLogin.setBackgroundResource(R.drawable.bg_button);
        restoreInputs();
        if (layoutAccount != null) {
            layoutAccount.setEndIconOnClickListener(v -> showSavedAccountPicker());
        }
        buttonLogin.setOnClickListener(v -> performLogin());

        String sid = apiClient.prefs().getString(ApiClient.KEY_SID, "");
        String host = apiClient.prefs().getString(ApiClient.KEY_HOST, "");
        boolean skipAutoLogin = getIntent().getBooleanExtra(EXTRA_SKIP_AUTO_LOGIN, false);
        if (!skipAutoLogin && !TextUtils.isEmpty(sid) && !TextUtils.isEmpty(host)) {
            finishLoginFlow();
        }
    }

    private void restoreInputs() {
        SharedPreferences prefs = apiClient.prefs();
        boolean rememberAccount = prefs.getBoolean(ApiClient.KEY_REMEMBER_ACCOUNT, true);
        boolean rememberPassword = prefs.getBoolean(ApiClient.KEY_REMEMBER_PASSWORD, true);
        boolean forceHttps = prefs.getBoolean(ApiClient.KEY_FORCE_HTTPS, true);
        boolean ignoreCert = prefs.getBoolean(ApiClient.KEY_IGNORE_CERT, true);

        checkRememberAccount.setChecked(rememberAccount);
        checkRememberPassword.setChecked(rememberPassword);
        checkForceHttps.setChecked(forceHttps);
        checkIgnoreCert.setChecked(ignoreCert);

        editHost.setText(prefs.getString(ApiClient.KEY_HOST, ""));
        editAccount.setText(rememberAccount ? prefs.getString(ApiClient.KEY_ACCOUNT, "") : "");
        editPassword.setText(rememberPassword ? prefs.getString(ApiClient.KEY_PASSWORD, "") : "");
    }

    private void performLogin() {
        String host = textOf(editHost);
        String account = textOf(editAccount);
        String password = textOf(editPassword);
        boolean rememberAccount = checkRememberAccount.isChecked();
        boolean rememberPassword = checkRememberPassword.isChecked();
        boolean forceHttps = checkForceHttps.isChecked();
        boolean ignoreCert = checkIgnoreCert.isChecked();

        if (TextUtils.isEmpty(host)) {
            toast(getString(R.string.please_enter_server_address));
            return;
        }
        if (TextUtils.isEmpty(account)) {
            toast(getString(R.string.please_enter_account));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            toast(getString(R.string.please_enter_password));
            return;
        }

        apiClient.prefs().edit()
                .putBoolean(ApiClient.KEY_REMEMBER_ACCOUNT, rememberAccount)
                .putBoolean(ApiClient.KEY_REMEMBER_PASSWORD, rememberPassword)
                .putBoolean(ApiClient.KEY_FORCE_HTTPS, forceHttps)
                .putBoolean(ApiClient.KEY_IGNORE_CERT, ignoreCert)
                .putString(ApiClient.KEY_ACCOUNT, rememberAccount ? account : "")
                .putString(ApiClient.KEY_PASSWORD, rememberPassword ? password : "")
                .apply();

        setLoading(true, getString(R.string.logging_in_and_saving_sid));
        apiClient.login(host, account, password, forceHttps, ignoreCert, new ApiClient.JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                setLoading(false, getString(R.string.login_success_sid_saved));
                finishLoginFlow();
            }

            @Override
            public void onError(String message) {
                setLoading(false, message);
                toast(message);
            }
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void finishLoginFlow() {
        if (apiClient.hasCustomSongCacheDirectory()) {
            markCacheLocationPromptDone();
            openMain();
            return;
        }
        if (apiClient.prefs().getBoolean(ApiClient.KEY_CACHE_LOCATION_PROMPT_DONE, false)) {
            openMain();
            return;
        }
        showCacheLocationDialog();
    }

    private void showCacheLocationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.set_cache_directory))
                .setMessage(getString(R.string.cache_dir_description))
                .setPositiveButton(getString(R.string.use_internal), (dialog, which) -> {
                    apiClient.setSongCacheTreeUri(null);
                    markCacheLocationPromptDone();
                    openMain();
                })
                .setNeutralButton(getString(R.string.select_external), (dialog, which) -> launchPickCacheLocation())
                .setNegativeButton(getString(R.string.close), (dialog, which) -> {
                    apiClient.setSongCacheTreeUri(null);
                    markCacheLocationPromptDone();
                    openMain();
                })
                .setCancelable(false)
                .show();
    }

    private void launchPickCacheLocation() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        pickCacheLocationLauncher.launch(intent);
    }

    private void handlePickCacheLocationResult(int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            apiClient.setSongCacheTreeUri(null);
            markCacheLocationPromptDone();
            openMain();
            return;
        }
        Uri treeUri = data.getData();
        if (treeUri == null) {
            apiClient.setSongCacheTreeUri(null);
            markCacheLocationPromptDone();
            openMain();
            return;
        }
        try {
            int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (SecurityException ignored) {
        }
        apiClient.setSongCacheTreeUri(treeUri);
        markCacheLocationPromptDone();
        openMain();
    }

    private void markCacheLocationPromptDone() {
        apiClient.prefs().edit().putBoolean(ApiClient.KEY_CACHE_LOCATION_PROMPT_DONE, true).apply();
    }

    private void showSavedAccountPicker() {
        List<ApiClient.AccountProfile> profiles = apiClient.readAccountProfiles();
        if (profiles.isEmpty()) {
            toast(getString(R.string.no_saved_account));
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_account_switch, null, false);
        ((TextView) view.findViewById(R.id.textAccountSwitchTitle)).setText(getString(R.string.select_account));
        TextView subtitle = view.findViewById(R.id.textAccountSwitchSubtitle);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerAccountProfiles);
        TextView buttonLogout = view.findViewById(R.id.buttonAccountSwitchLogout);
        TextView buttonAdd = view.findViewById(R.id.buttonAccountSwitchAdd);
        TextView buttonClose = view.findViewById(R.id.buttonAccountSwitchClose);

        subtitle.setText(getString(R.string.reuse_saved_config));
        buttonLogout.setVisibility(View.GONE);
        buttonAdd.setVisibility(View.GONE);
        buttonClose.setText(getString(R.string.close));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new SavedAccountAdapter(profiles, profile -> {
            applyProfileToInputs(profile);
            dialog.dismiss();
        }));
        buttonClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void applyProfileToInputs(ApiClient.AccountProfile profile) {
        if (profile == null) {
            return;
        }
        editHost.setText(profile.host == null ? "" : profile.host);
        editAccount.setText(profile.account == null ? "" : profile.account);
        if (profile.password != null && !profile.password.isEmpty()) {
            editPassword.setText(profile.password);
        }
        checkForceHttps.setChecked(profile.forceHttps);
        checkIgnoreCert.setChecked(profile.ignoreCert);
        checkRememberAccount.setChecked(true);
        checkRememberPassword.setChecked(profile.password != null && !profile.password.isEmpty());
    }

    private final class SavedAccountAdapter extends RecyclerView.Adapter<SavedAccountAdapter.Holder> {
        private final List<ApiClient.AccountProfile> profiles;
        private final SavedAccountHandler handler;

        SavedAccountAdapter(List<ApiClient.AccountProfile> profiles, SavedAccountHandler handler) {
            this.profiles = profiles == null ? new ArrayList<>() : profiles;
            this.handler = handler;
        }

        @Override
        public Holder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            return new Holder(getLayoutInflater().inflate(R.layout.item_account_profile, parent, false));
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ApiClient.AccountProfile profile = profiles.get(position);
            holder.textTitle.setText(profile.account == null ? "" : profile.account);
            String hostLabel = (profile.host == null ? "" : profile.host)
                    .replace("https://", "")
                    .replace("http://", "");
            holder.textSubtitle.setText(hostLabel);
            StringBuilder meta = new StringBuilder();
            meta.append(profile.forceHttps ? "HTTPS" : "HTTP");
            meta.append(" \u00b7 ");
            meta.append(profile.ignoreCert ? getString(R.string.ignore_certificate) : getString(R.string.verify_certificate));
            if (profile.password != null && !profile.password.isEmpty()) {
                meta.append(" · " + getString(R.string.remember_password_saved));
            }
            holder.textMeta.setText(meta.toString());
            holder.imageCurrent.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (handler != null) {
                    handler.onPick(profile);
                }
            });
        }

        @Override
        public int getItemCount() {
            return profiles.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final TextView textTitle;
            final TextView textSubtitle;
            final TextView textMeta;
            final android.widget.ImageView imageAvatar;
            final android.widget.ImageView imageCurrent;

            Holder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textAccountTitle);
                textSubtitle = itemView.findViewById(R.id.textAccountSubtitle);
                textMeta = itemView.findViewById(R.id.textAccountMeta);
                imageAvatar = itemView.findViewById(R.id.imageAccountAvatar);
                imageCurrent = itemView.findViewById(R.id.imageAccountCurrent);
            }
        }
    }

    private interface SavedAccountHandler {
        void onPick(ApiClient.AccountProfile profile);
    }

    private void setLoading(boolean loading, String message) {
        buttonLogin.setEnabled(!loading);
        buttonLogin.setAlpha(loading ? 0.65f : 1f);
        textStatus.setText(message);
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
