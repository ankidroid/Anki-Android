/*
 * Copyright 2012 Evernote Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.client.android;

import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.type.LinkedNotebook;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.SharedNotebook;
import com.evernote.thrift.TException;
import com.evernote.thrift.protocol.TProtocol;
import com.evernote.thrift.transport.TTransportException;

import java.util.Arrays;
import java.util.List;

/**
 *
 * This is a wrapper/helper class that manages the connection to a linked notestore. It maintains two
 * {@link AsyncLinkedNoteStoreClient} objects, one points to the users personal store and the other to
 * linked notebooks shard.
 *
 * These helper methods make network calls across both shards to return the appropriate data.
 *
 *
 *
 * @author @tylersmithnet
 */
public class AsyncLinkedNoteStoreClient {
  /**
   * References users main note store
   */
  private AsyncNoteStoreClient mMainNoteStoreClient;
  private AsyncNoteStoreClient mLinkedStoreClient;
  private String mAuthToken;
  private ClientFactory mClientFactory;

  AsyncLinkedNoteStoreClient(TProtocol iprot, TProtocol oprot, String authenticationToken, ClientFactory clientFactory) throws TTransportException {
    mLinkedStoreClient = new AsyncNoteStoreClient(iprot, oprot, authenticationToken);
    mMainNoteStoreClient = EvernoteSession.getOpenSession().getClientFactory().createNoteStoreClient();
    mAuthToken = authenticationToken;
    mClientFactory = clientFactory;
  }

  /**
   * Returns the {@link AsyncNoteStoreClient} object that has been instantiated to the appropriate shard
   * @return
   */
  public AsyncNoteStoreClient getAsyncClient() {
    return mLinkedStoreClient;
  }

  AsyncNoteStoreClient getAsyncPersonalClient() {
    return mMainNoteStoreClient;
  }

  String getAuthenticationToken() {
    return mAuthToken;
  }

  void setAuthToken(String authenticationToken) {
    mAuthToken = authenticationToken;
  }

  ClientFactory getClientFactory() {
    return mClientFactory;
  }

  /**
   * Helper method to create a note asynchronously in a linked/business notebook
   *
   * @param note
   * @param linkedNotebook
   * @param callback
   */
  public void createNoteAsync(final Note note, final LinkedNotebook linkedNotebook, final OnClientCallback<Note> callback) {
    AsyncReflector.execute(this, callback, "createNote", note, linkedNotebook);

  }

  /**
   * Helper method to create a note synchronously in a linked notebook
   *
   * @param note
   * @param linkedNotebook
   * @return
   * @throws com.evernote.edam.error.EDAMUserException
   *
   * @throws com.evernote.edam.error.EDAMSystemException
   *
   * @throws com.evernote.thrift.TException
   * @throws com.evernote.edam.error.EDAMNotFoundException
   *
   */
  public Note createNote(Note note, LinkedNotebook linkedNotebook) throws EDAMUserException, EDAMSystemException, TException, EDAMNotFoundException {

    SharedNotebook sharedNotebook = getAsyncClient().getClient().getSharedNotebookByAuth(getAuthenticationToken());
    note.setNotebookGuid(sharedNotebook.getNotebookGuid());
    return getAsyncClient().getClient().createNote(getAuthenticationToken(), note);

  }

  /**
   * Helper method to list linked/business notebooks asynchronously
   *
   * @see {@link com.evernote.edam.notestore.NoteStore.Client#listLinkedNotebooks(String)}
   *
   * @param callback
   */
  public void listNotebooksAsync(final OnClientCallback<List<LinkedNotebook>> callback) {
    AsyncReflector.execute(getAsyncPersonalClient(), callback, "listNotebooks", getAuthenticationToken());
  }

  /**
   * Helper method to list linked notebooks synchronously
   *
   * @see {@link com.evernote.edam.notestore.NoteStore.Client#listLinkedNotebooks(String)}
   *
   */
  public List<LinkedNotebook> listNotebooks() throws EDAMUserException, EDAMSystemException, TException, EDAMNotFoundException {
    return getAsyncPersonalClient().getClient().listLinkedNotebooks(getAsyncPersonalClient().getAuthenticationToken());
  }

  /**
   * Create Linked Notebook from a Notebook
   *
   * Asynchronous call
   *
   * @param callback
   */
  public void createNotebookAsync(Notebook notebook, OnClientCallback<LinkedNotebook> callback) {
    AsyncReflector.execute(this, callback, "createNotebook", notebook);
  }

  /**
   * Create Linked Notebook from a Notebook
   *
   * Synchronous call
   *
   * @return {@link LinkedNotebook} with guid from server
   */
  public LinkedNotebook createNotebook(Notebook notebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {

    Notebook originalNotebook = getAsyncClient().getClient().createNotebook(getAuthenticationToken(), notebook);

    SharedNotebook sharedNotebook = originalNotebook.getSharedNotebooks().get(0);
    LinkedNotebook linkedNotebook = new LinkedNotebook();
    linkedNotebook.setShareKey(sharedNotebook.getShareKey());
    linkedNotebook.setShareName(originalNotebook.getName());
    linkedNotebook.setUsername(EvernoteSession.getOpenSession().getAuthenticationResult().getBusinessUser().getUsername());
    linkedNotebook.setShardId(EvernoteSession.getOpenSession().getAuthenticationResult().getBusinessUser().getShardId());

    return getAsyncPersonalClient().getClient().createLinkedNotebook(getAsyncPersonalClient().getAuthenticationToken(), linkedNotebook);
  }

  /**
   * Providing a LinkedNotebook referencing a linked/business account, perform a delete
   *
   * Asynchronous call
   * @param callback
   */
  public void deleteNotebookAsync(LinkedNotebook linkedNotebook, OnClientCallback<Integer> callback) {
    AsyncReflector.execute(this, callback, "deleteNotebook", linkedNotebook);
  }

  /**
   * Providing a LinkedNotebook referencing a linked account, perform a delete
   *
   * Synchronous call
   *
   * @return guid of notebook deleted
   */
  public int deleteNotebook(LinkedNotebook linkedNotebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {

    SharedNotebook sharedNotebook = getAsyncClient().getClient().getSharedNotebookByAuth(getAuthenticationToken());

    Long[] ids = {sharedNotebook.getId()};
    getAsyncClient().getClient().expungeSharedNotebooks(getAuthenticationToken(), Arrays.asList(ids));
    return getAsyncPersonalClient().getClient().expungeLinkedNotebook(getAsyncPersonalClient().getAuthenticationToken(), linkedNotebook.getGuid());
  }

  /**
   * Will return the {@link Notebook} associated with the {@link LinkedNotebook} from the linked/business account
   *
   * Asynchronous call
   *
   * @param linkedNotebook
   * @param callback
   */
  public void getCorrespondingNotebookAsync(LinkedNotebook linkedNotebook, OnClientCallback<Notebook> callback) {
    AsyncReflector.execute(this, callback, "getCorrespondingNotebook", linkedNotebook);
  }

  /**
   * Will return the {@link com.evernote.edam.type.Notebook} associated with the {@link com.evernote.edam.type.LinkedNotebook} from the linked account
   *
   * Synchronous call
   *
   * @param linkedNotebook
   */
  public Notebook getCorrespondingNotebook(LinkedNotebook linkedNotebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {
    SharedNotebook sharedNotebook = getAsyncClient().getClient().getSharedNotebookByAuth(getAuthenticationToken());
    return getAsyncClient().getClient().getNotebook(getAuthenticationToken(), sharedNotebook.getNotebookGuid());
  }

  /**
   * Checks writable permissions of {@link LinkedNotebook} on Linked/business account
   *
   * Asynchronous call
   *
   * @param linkedNotebook
   * @param callback
   */
  public void isNotebookWritableAsync(LinkedNotebook linkedNotebook, OnClientCallback<Boolean> callback) {
    AsyncReflector.execute(this, callback, "isLinkedNotebookWritable", linkedNotebook);
  }
  /**
   * Checks writable permissions of {@link LinkedNotebook} on Linked account
   *
   * Synchronous call
   *
   * @param linkedNotebook
   */
  public boolean isNotebookWritable(LinkedNotebook linkedNotebook) throws EDAMUserException, TException, EDAMSystemException, EDAMNotFoundException {
    Notebook notebook = getCorrespondingNotebook(linkedNotebook);
    return !notebook.getRestrictions().isNoCreateNotes();
  }
}
