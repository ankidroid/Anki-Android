package com.ichi2.anki.provider;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;

import com.ichi2.anki.BuildConfig;
import com.ichi2.compat.CompatHelper;

import java.util.Arrays;

import androidx.annotation.Nullable;
import timber.log.Timber;

import static com.ichi2.anki.FlashCardsContract.READ_WRITE_PERMISSION;
import static com.ichi2.anki.StatsContract.READ_PERMISSION;

public class ProviderUtils {



    /** Only enforce permissions for queries and inserts on Android M and above, or if its a 'rogue client' **/
    public static boolean shouldEnforceQueryOrInsertSecurity(ContentProvider provider, Context context) {
        return CompatHelper.isMarshmallow() || knownRogueClient(provider, context);
    }

    public static boolean hasReadPermission(Context context) {
        if (BuildConfig.DEBUG) {    // Allow self-calling of the provider only in debug builds (e.g. for unit tests)
            return hasReadWritePermission(context) || context.checkCallingOrSelfPermission(READ_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        }
        return hasReadWritePermission(context) || context.checkCallingPermission(READ_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasReadWritePermission(Context context) {
        if (BuildConfig.DEBUG) {    // Allow self-calling of the provider only in debug builds (e.g. for unit tests)
            return context.checkCallingOrSelfPermission(READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
        }
        return context.checkCallingPermission(READ_WRITE_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void throwSecurityException(ContentProvider provider, Context context, String methodName, Uri uri) {
        String msg = String.format("Permission not granted for: %s", getLogMessage(provider, context, methodName, uri));
        Timber.e(msg);
        throw new SecurityException(msg);
    }

    public static String getLogMessage(ContentProvider provider, Context context, String methodName, Uri uri) {
        final String format = "%s.%s %s (%s)";
        String path = uri == null ? null : uri.getPath();
        return String.format(format, provider.getClass().getSimpleName(), methodName, path, getCallingPackageSafe(provider, context));
    }

    /** Returns true if the calling package is known to be "rogue" and should be blocked.
     Calling package might be rogue if it has not declared #READ_WRITE_PERMISSION in its manifest, or if blacklisted **/
    public static boolean knownRogueClient(ContentProvider provider, Context context) {
        final PackageManager pm = context.getPackageManager();
        try {
            PackageInfo callingPi = pm.getPackageInfo(getCallingPackageSafe(provider, context), PackageManager.GET_PERMISSIONS);
            if (callingPi == null || callingPi.requestedPermissions == null) {
                return false;
            }
            return !Arrays.asList(callingPi.requestedPermissions).contains(READ_WRITE_PERMISSION);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Nullable
    private static String getCallingPackageSafe(ContentProvider provider, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return provider.getCallingPackage();
        }
        String[] pkgs = context.getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (pkgs.length == 1) {
            return pkgs[0]; // This is usual case, unless multiple packages signed with same key & using "sharedUserId"
        }
        return null;
    }
}
