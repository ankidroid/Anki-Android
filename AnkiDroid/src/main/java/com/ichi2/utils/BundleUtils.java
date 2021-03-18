package com.ichi2.utils;

import android.os.Bundle;

import com.ichi2.libanki.Utils;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Collection of useful methods to be used with {@link android.os.Bundle}
 */
public class BundleUtils {


    /**
     * Retrieves a {@link Long} value from a {@link Bundle} using a key, returns null if not found
     *
     * @param bundle the bundle to look into
     *               can be null to support nullable bundles like {@link Fragment#getArguments()}
     * @param key the key to use
     * @return the long value, or null if not found
     */
    @Nullable
    public static Long getNullableLong(@Nullable Bundle bundle, @NonNull String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        return bundle.getLong(key);
    }


    /**
     * Retrieves a {@link List<Long>} value from a {@link Bundle} using a key, returns null if not found
     *
     * @param bundle the bundle to look into
     *               can be null to support nullable bundles like {@link Fragment#getArguments()}
     * @param key the key to use
     * @return the long list, or null if not found
     */
    @Nullable
    public static List<Long> getNullableLongList(@Nullable Bundle bundle, @NonNull String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return null;
        }
        return Utils.primitiveArray2List(bundle.getLongArray(key));
    }

}
