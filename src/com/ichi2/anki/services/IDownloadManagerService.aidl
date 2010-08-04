/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ichi2.anki.services;

import com.ichi2.anki.services.IPersonalDeckServiceCallback;
import com.ichi2.anki.services.ISharedDeckServiceCallback;
import com.ichi2.anki.Download;
import com.ichi2.anki.SharedDeckDownload;

/**
 * Example of defining an interface for calling on to a remote service
 * (running in another process).
 */
interface IDownloadManagerService {
    /**
     * Often you want to allow a service to call back to its clients.
     * This shows how to do so, by registering a callback interface with
     * the service.
     */
    void registerPersonalDeckCallback(IPersonalDeckServiceCallback cb);
    
    /**
     * Remove a previously registered callback interface.
     */
    void unregisterPersonalDeckCallback(IPersonalDeckServiceCallback cb);
    
    /**
     * Often you want to allow a service to call back to its clients.
     * This shows how to do so, by registering a callback interface with
     * the service.
     */
    void registerSharedDeckCallback(ISharedDeckServiceCallback cb);
    
    /**
     * Remove a previously registered callback interface.
     */
    void unregisterSharedDeckCallback(ISharedDeckServiceCallback cb);
    
    List<Download> getPersonalDeckDownloads();
    
    List<SharedDeckDownload> getSharedDeckDownloads();
    
    void downloadFile(in Download download);
}
