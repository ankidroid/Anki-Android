//noinspection MissingCopyrightHeader #8659
// Copyright 2015 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.ichi2.compat.customtabs;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.R;
import com.ichi2.anki.UIUtils;

/**
 * A Fallback that opens a Webview when Custom Tabs is not available
 */
public class CustomTabsFallback implements CustomTabActivityHelper.CustomTabFallback {
    @Override
    public void openUri(Activity activity, Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            // This can occur if the provider is not exported: #7721
            // this should not happen as we don't reach here if there's no valid browser.
            // and I assume an exported intent will take priority over a non-exported intent.
            // Add an exception report to see if I'm wrong
            AnkiDroidApp.sendExceptionReport(e, "CustomTabsFallback::openUri");
            UIUtils.showThemedToast(activity, activity.getString(R.string.web_page_error, uri), false);
        }
    }
}
