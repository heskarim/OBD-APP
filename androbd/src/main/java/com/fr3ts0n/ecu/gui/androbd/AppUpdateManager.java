package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppUpdateManager
{
    private static final String PREFS_NAME = "app_update";
    private static final String KEY_LAST_AUTO_CHECK = "last_auto_check";
    private static final long AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final Pattern VERSION_CODE_PATTERN =
            Pattern.compile("version_code\\s*=\\s*(\\d+)");

    private AppUpdateManager()
    {
    }

    static void checkAutomatically(Activity activity)
    {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_AUTO_CHECK, 0L) < AUTO_CHECK_INTERVAL_MS)
        {
            return;
        }
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK, now).apply();
        checkForUpdates(activity, false);
    }

    static void checkNow(Activity activity)
    {
        checkForUpdates(activity, true);
    }

    private static void checkForUpdates(Activity activity, boolean userInitiated)
    {
        new Thread(() ->
        {
            try
            {
                ReleaseInfo release = fetchLatestRelease();
                if (release.versionCode <= BuildConfig.VERSION_CODE)
                {
                    if (userInitiated)
                    {
                        showToast(activity, activity.getString(R.string.update_already_current));
                    }
                    return;
                }
                activity.runOnUiThread(() -> showUpdateDialog(activity, release));
            }
            catch (Exception e)
            {
                if (userInitiated)
                {
                    showToast(activity, activity.getString(R.string.update_check_failed, e.getMessage()));
                }
            }
        }, "AppUpdateCheck").start();
    }

    private static ReleaseInfo fetchLatestRelease() throws Exception
    {
        String apiUrl = String.format(
                Locale.US,
                "https://api.github.com/repos/%s/%s/releases/latest",
                BuildConfig.GITHUB_UPDATE_OWNER,
                BuildConfig.GITHUB_UPDATE_REPO);
        JSONObject release = new JSONObject(readString(apiUrl));
        int versionCode = parseVersionCode(release.optString("body", ""));
        String versionName = release.optString("name", release.optString("tag_name", ""));
        String apkUrl = null;
        String apkName = null;

        JSONArray assets = release.optJSONArray("assets");
        if (assets != null)
        {
            for (int i = 0; i < assets.length(); i++)
            {
                JSONObject asset = assets.getJSONObject(i);
                String name = asset.optString("name", "");
                if (name.endsWith(".apk"))
                {
                    apkName = name;
                    apkUrl = asset.optString("browser_download_url", null);
                    break;
                }
            }
        }

        if (versionCode <= 0 || apkUrl == null)
        {
            throw new IllegalStateException("latest release has no update APK metadata");
        }
        return new ReleaseInfo(versionCode, versionName, apkName, apkUrl);
    }

    private static int parseVersionCode(String body)
    {
        Matcher matcher = VERSION_CODE_PATTERN.matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String readString(String url) throws Exception
    {
        HttpURLConnection connection = openConnection(url);
        try (InputStream input = connection.getInputStream())
        {
            byte[] buffer = new byte[8192];
            StringBuilder result = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                result.append(new String(buffer, 0, read));
            }
            return result.toString();
        }
        finally
        {
            connection.disconnect();
        }
    }

    private static void showUpdateDialog(Activity activity, ReleaseInfo release)
    {
        if (activity.isFinishing())
        {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(activity.getString(
                        R.string.update_available_message,
                        release.versionName,
                        release.versionCode))
                .setPositiveButton(R.string.update_install, (dialog, which) ->
                        downloadAndInstall(activity, release))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void downloadAndInstall(Activity activity, ReleaseInfo release)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls())
        {
            showToast(activity, activity.getString(R.string.update_install_permission_needed));
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return;
        }

        new Thread(() ->
        {
            try
            {
                showToast(activity, activity.getString(R.string.update_downloading));
                File apk = downloadApk(activity, release);
                installApk(activity, apk);
            }
            catch (Exception e)
            {
                showToast(activity, activity.getString(R.string.update_download_failed, e.getMessage()));
            }
        }, "AppUpdateDownload").start();
    }

    private static File downloadApk(Context context, ReleaseInfo release) throws Exception
    {
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null)
        {
            dir = context.getCacheDir();
        }
        if (!dir.exists() && !dir.mkdirs())
        {
            throw new IllegalStateException("update download directory is unavailable");
        }

        File apk = new File(dir, "androbd-update-" + release.versionCode + ".apk");
        HttpURLConnection connection = openConnection(release.apkUrl);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(apk))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, read);
            }
        }
        finally
        {
            connection.disconnect();
        }
        return apk;
    }

    private static HttpURLConnection openConnection(String url) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "AndrOBD-auto-updater");
        connection.setInstanceFollowRedirects(true);
        return connection;
    }

    private static void installApk(Activity activity, File apk)
    {
        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".provider",
                apk);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static void showToast(Activity activity, String message)
    {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }

    private static final class ReleaseInfo
    {
        final int versionCode;
        final String versionName;
        final String apkName;
        final String apkUrl;

        ReleaseInfo(int versionCode, String versionName, String apkName, String apkUrl)
        {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
        }
    }
}
