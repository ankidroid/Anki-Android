/****************************************************************************************
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>                       *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.Log;

public class PluginManager {
	private static PluginManager sInstance;
    public static final String PLUGIN_ACTION_USE = "com.ichi2.anki.plugin.action.USE_PLUGIN";
    public static final String PLUGIN_TYPE_CONTROLLER = "com.ichi2.anki.plugin.controller";

    private Map<String, Map<String, String>> mControllers;
    
    private PluginManager() {
    	mControllers = new HashMap<String, Map<String, String>>();
    }

    static public PluginManager getPluginManager() {
    	if (sInstance == null) {
    		sInstance = new PluginManager();
    	}
    	return sInstance;
    }

    public boolean hasPlugins() {
    	return !mControllers.isEmpty();
    }

    public Map<String, Map<String, String>> getControllerPlugins() {
    	return mControllers;
    }

    public String getEnabledControllerPlugin(Context ctx) {
    	SharedPreferences prefs = AnkiDroidApp.getSharedPrefs(ctx);
    	for (String pluginKey : mControllers.keySet()) {
    		if (prefs.getBoolean(Preferences.PLUGIN_PREFERENCE_PREFIX + pluginKey, false)) {
    			return pluginKey;
    		}
    	}
        return null;
    }
    
    public Intent getPluginIntent(String key) {
    	if (mControllers.containsKey(key)) {
    		Intent intent = new Intent(PluginManager.PLUGIN_ACTION_USE);
    		intent.addCategory(getControllerPlugins().get(key).get("category"));
    		intent.setFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
    		return intent;
    	}
    	return null;
    }

    public void discoverPlugins() {
    	Context ctx = AnkiDroidApp.getInstance().getBaseContext();
    	mControllers.clear();
    	
    	PackageManager pm = ctx.getPackageManager();
    	Intent findPlugins = new Intent(PLUGIN_ACTION_USE);
    	findPlugins.setFlags(Intent.FLAG_DEBUG_LOG_RESOLUTION);
    	List<ResolveInfo> list = pm.queryIntentServices(findPlugins, PackageManager.GET_RESOLVED_FILTER );
    	for (ResolveInfo ri : list) {
    		ServiceInfo si = ri.serviceInfo;
    		IntentFilter ifl = ri.filter;
    		if (si != null && ifl != null && ifl.hasAction(PLUGIN_ACTION_USE)) {
    			String pkgName = si.applicationInfo.packageName;
    			String name = pm.getApplicationLabel(si.applicationInfo).toString();
        		Log.d(AnkiDroidApp.TAG, "Found plugin, pkg: " + pkgName + ", name: " + name);
				for (Iterator<String> catit = ifl.categoriesIterator(); catit.hasNext();) {
					String category = catit.next();
					if (category.startsWith(PLUGIN_TYPE_CONTROLLER)) {
						Log.d(AnkiDroidApp.TAG, "Plugin " + name + " is of type controller");
						Map<String, String> controller = new HashMap<String, String>();
						controller.put("package", pkgName);
						controller.put("name", name);
						controller.put("category", category);
						controller.put("key", pkgName + "/" + name);
						mControllers.put(controller.get("key"), controller);
					}
				}
    		}
    	}
    }
    
    public void pluginPrefChanged(Context ctx, String pluginKey, boolean newValue) {
    	if (mControllers.containsKey(pluginKey)) {
    		AnkiDroidApp.getControllerManager().controllerPrefChanged(ctx,
    				mControllers.get(pluginKey).get("name"), newValue);
    	}
    }
}
