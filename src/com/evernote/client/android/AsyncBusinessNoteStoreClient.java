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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * This is a wrapper/helper class that manages the connection to a business notestore. It maintains two
 * {@link AsyncLinkedNoteStoreClient} objects, one points to the users personal store and the other to
 * the business shard.
 *
 * These helper methods make network calls across both shards to return the appropriate data.
 *
 *
 *
 * @author @tylersmithnet
 */
public class AsyncBusinessNoteStoreClient extends AsyncLinkedNoteStoreClient{
  /**
   * Reference to your personal note store
   */

  AsyncBusinessNoteStoreClient(TProtocol iprot, TProtocol oprot, String authenticationToken, ClientFactory clientFactory) throws TTransportException {
    super(iprot, oprot, authenticationToken, clientFactory);
  }

  /**
   * Helper method to create a note synchronously in a business notebook
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
  @Override
  public Note createNote(Note note, LinkedNotebook linkedNotebook) throws EDAMUserException, EDAMSystemException, TException, EDAMNotFoundException {

    AsyncLinkedNoteStoreClient sharedNoteStore = getClientFactory().createLinkedNoteStoreClient(linkedNotebook);
    SharedNotebook sharedNotebook = sharedNoteStore.getAsyncClient().getClient().getSharedNotebookByAuth(sharedNoteStore.getAuthenticationToken());

    note.setNotebookGuid(sharedNotebook.getNotebookGuid());
    sharedNoteStore.getAsyncClient().getClient().createNote(sharedNoteStore.getAuthenticationToken(), note);

    return note;
  }

  /**
   * Helper method to list business notebooks synchronously
   *
   * @return
   * @throws EDAMUserException
   * @throws EDAMSystemException
   * @throws TException
   * @throws EDAMNotFoundException
   */
  @Override
  public List<LinkedNotebook> listNotebooks() throws EDAMUserException, EDAMSystemException, TException, EDAMNotFoundException {

    List<LinkedNotebook> linkedNotebooks = new ArrayList<LinkedNotebook>();
    for (LinkedNotebook notebook : super.listNotebooks()) {
      if (notebook.isSetBusinessId()) {
        linkedNotebooks.add(notebook);
      }
    }
    return linkedNotebooks;
  }

  /**
   * Create Business Notebook from a Notebook
   *
   * Synchronous call
   *
   * @return {@link LinkedNotebook} with guid from server
   */
  @Override
  public LinkedNotebook createNotebook(Notebook notebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {
    return super.createNotebook(notebook);
  }

  /**
   * Providing a LinkedNotebook referencing a Business notebook, perform a delete
   *
   * Synchronous call
   *
   * @return guid of notebook deleted
   */
  @Override
  public int deleteNotebook(LinkedNotebook linkedNotebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {

    AsyncLinkedNoteStoreClient sharedNoteStore = getClientFactory().createLinkedNoteStoreClient(linkedNotebook);
    SharedNotebook sharedNotebook = sharedNoteStore.getAsyncClient().getClient().getSharedNotebookByAuth(sharedNoteStore.getAuthenticationToken());

    Long[] ids = {sharedNotebook.getId()};
    getAsyncClient().getClient().expungeSharedNotebooks(getAuthenticationToken(), Arrays.asList(ids));
    return getAsyncPersonalClient().getClient().expungeLinkedNotebook(getAsyncPersonalClient().getAuthenticationToken(), linkedNotebook.getGuid());
  }

  /**
   * Will return the {@link Notebook} associated with the {@link LinkedNotebook} from the business account
   *
   * Synchronous call
   *
   * @param linkedNotebook
   */
  @Override
  public Notebook getCorrespondingNotebook(LinkedNotebook linkedNotebook) throws TException, EDAMUserException, EDAMSystemException, EDAMNotFoundException {
    //Get LinkedStore for auth information
    AsyncLinkedNoteStoreClient sharedNoteStore = getClientFactory().createLinkedNoteStoreClient(linkedNotebook);
    SharedNotebook sharedNotebook = sharedNoteStore.getAsyncClient().getClient().getSharedNotebookByAuth(sharedNoteStore.getAuthenticationToken());

    return getAsyncClient().getClient().getNotebook(getAuthenticationToken(), sharedNotebook.getNotebookGuid());
  }
}
