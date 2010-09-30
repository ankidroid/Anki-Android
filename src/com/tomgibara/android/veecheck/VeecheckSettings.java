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

/**
 * Provides settings for a {@link VeecheckReceiver} instance.
 * 
 * @author Tom Gibara
 */

public interface VeecheckSettings {

    /**
     * Whether checking should be performed.
     * 
     * @return true if checking should be performed
     */

    boolean isEnabled();


    /**
     * The time-interval, in milliseconds, at which the {@link VeecheckReceiver} will consider checking for new
     * application versions.
     * 
     * @return a strictly positive number
     */

    long getPeriod();


    /**
     * The URI from which the versions document is downloaded. Currently, URIs are restricted to the http scheme. The
     * URI may be subject to substitution with tokens.
     * 
     * @return the URI from which the versions document will be downloaded
     */

    String getCheckUri();


    /**
     * The length of time, in milliseconds, for which the application version will not be checked by the
     * {@link VeecheckReceiver}
     * 
     * @return a strictly positive number
     */

    long getCheckInterval();

}
