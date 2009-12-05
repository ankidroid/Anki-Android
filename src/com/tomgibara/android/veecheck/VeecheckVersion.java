/*
 * Copyright 2008 Tom Gibara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomgibara.android.veecheck;

import static com.tomgibara.android.veecheck.Veecheck.LOG_TAG;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.xml.sax.Attributes;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

/**
 * Instances of this class collate information about the current application.
 * Some of the properties are obtained from the package manager, others from
 * the device build.
 * 
 * @author Tom Gibara
 *
 */

class VeecheckVersion {

	/**
	 * Utility method that substitutes null for empty strings.
	 *  
	 * @param str a string that may be empty
	 * @return non-empty string, or null
	 */
	
	private static String emptyToNull(String str) {
		return str == null || str.length() == 0 ? null : str;
	}
	
	/**
	 * The package name of the application.
	 */
	
	String packageName;
	
	/**
	 * The version code of the application.
	 */
	
	String versionCode;
	
	/**
	 * The version name of the application.
	 */
	
	String versionName;
	
	/**
	 * The brand associated with the device's build.
	 */
	
	String buildBrand;
	
	/**
	 * The ID of the device's build.
	 */
	
	String buildId;
	
	/**
	 * The device model.
	 */
	
	String buildModel;
	
	/**
	 * Constructs a version with uniformly null version properties.
	 */
	
	VeecheckVersion() {
	}
	
	/**
	 * Constructs a version that draws all of its properties from the supplied
	 * context.
	 * 
	 * @param context the context from which version properties will be drawn
	 */
	
	VeecheckVersion(Context context) {
		try {
			PackageManager manager = context.getPackageManager();
			PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
			packageName = info.packageName;
			versionCode = Integer.toString(info.versionCode);
			versionName = info.versionName;
		} catch (NameNotFoundException e) {
			Log.w(LOG_TAG, "Unable to obtain package info: ", e);
		}
		buildBrand = Build.BRAND;
		buildId = Build.ID;
		buildModel = Build.MODEL;
	}

	/**
	 * Constructs a version that draws all of its properties from a collection
	 * of XML element attributes extracted by a SAX parser.
	 * 
	 * @param attrs the attributes from which version properties will be drawn
	 */
	
	VeecheckVersion(Attributes attrs) {
		packageName = emptyToNull( attrs.getValue("packageName") );
		versionCode = emptyToNull( attrs.getValue("versionCode") );
		versionName = emptyToNull( attrs.getValue("versionName") );
		buildBrand = emptyToNull( attrs.getValue("buildBrand") );
		buildId = emptyToNull( attrs.getValue("buildId") );
		buildModel = emptyToNull( attrs.getValue("buildModel") );
	}

	/**
	 * Replaces tokens of the form <tt>${name}</tt> with version properties in
	 * a supplied URL.
	 * 
	 * @param url the URL into which properties should be substituted
	 * 
	 * @return the resulting URL, unchanged if no tokens were present
	 */
	
	String substitute(String url) {
		//TODO could make more efficient
		if (packageName != null) url = substitute(url, "${package.name}", packageName);
		if (versionCode != null) url = substitute(url, "${version.code}", versionCode);
		if (versionName != null) url = substitute(url, "${version.name}", versionName);
		if (buildBrand != null) url = substitute(url, "${build.brand}", buildBrand);
		if (buildId != null) url = substitute(url, "${build.id}", buildId);
		if (buildModel != null) url = substitute(url, "${build.model}", buildModel);
		return url;
	}
	
	private String substitute(String str, String key, String value) {
		try {
			return str.replace("key", URLEncoder.encode(value, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			Log.w(LOG_TAG, "Unable to encode URL using UTF-8", e);
			return str;
		}
	}
	
	/**
	 * Matches this {@link VeecheckVersion} against another {@link VeecheckVersion}.
	 * This version matches the required version if their properties agree exactly
	 * on every non-null property of the required version.
	 * 
	 * @param required the version against which this version is being matched
	 * 
	 * @return true iff this version matches the required version
	 */
	
	public boolean matches(VeecheckVersion required) {
		if (required.packageName != null && !required.packageName.equals(this.packageName)) return false;
		if (required.versionCode != null && !required.versionCode.equals(this.versionCode)) return false;
		if (required.versionName != null && !required.versionName.equals(this.versionName)) return false;
		if (required.buildBrand != null && !required.buildBrand.equals(this.buildBrand)) return false;
		if (required.buildId != null && !required.buildId.equals(this.buildId)) return false;
		if (required.buildModel != null && !required.buildModel.equals(this.buildModel)) return false;
		return true;
	}
	
}
