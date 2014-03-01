package com.ichi2.anki;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Note;
import com.ichi2.themes.StyledProgressDialog;

import com.evernote.client.android.AsyncNoteStoreClient;
import com.evernote.client.android.EvernoteSession;
import com.evernote.client.android.InvalidAuthenticationException;
import com.evernote.client.android.OnClientCallback;

import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMSystemException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteFilter;
import com.evernote.edam.notestore.NoteList;
import com.evernote.thrift.TException;
import com.evernote.thrift.transport.TTransportException;

public class EvernoteSync {

	private static EvernoteSync singletonInstance;
	private static final String CONSUMER_KEY = "matthiasv-3883" ;
	private static final String CONSUMER_SECRET = "a944056d8952611c" ;
	private static final EvernoteSession.EvernoteService EVERNOTE_SERVICE = EvernoteSession.EvernoteService.PRODUCTION;
	protected static EvernoteSession mEvernoteSession;
	private Context context;
	private long LastSync;
	private SharedPreferences prefs;
	private Collection mCol;
	private String eSearchString;
	private int eTotalNotes = 0;
	private AsyncNoteStoreClient eNoteStore;
	private ArrayList<String> eNotes = new ArrayList<String>();
	static final private String ankiID_s = "anki";
	private StyledProgressDialog progressDialog;

	private EvernoteSync(Context ctx) throws Exception {
		this.context = ctx;
		if (!EvernoteAppInstalled("com.evernote")) {
			throw new Exception("Evernote app is not installed");
		} 
		mEvernoteSession = EvernoteSession.getInstance(context , CONSUMER_KEY , CONSUMER_SECRET , EVERNOTE_SERVICE );
	}
	
	public static EvernoteSync getInstance(Context ctx) {
		if (null == singletonInstance) {
			try {
				singletonInstance = new EvernoteSync(ctx);
			} catch (Exception e) {
				//Log.e(AnkiDroidApp.TAG, e.getMessage());
				e.printStackTrace();
			}
		}
		return singletonInstance;
	}

	public void login() {
		mEvernoteSession = EvernoteSession.getInstance(this.context , CONSUMER_KEY , CONSUMER_SECRET , EVERNOTE_SERVICE );
		mEvernoteSession.authenticate(this.context);
	}

	public void logout() {
		try {
			mEvernoteSession = EvernoteSession.getInstance(this.context, CONSUMER_KEY , CONSUMER_SECRET , EVERNOTE_SERVICE );
			mEvernoteSession.logOut(this.context);
		} catch (InvalidAuthenticationException e) {
			Log.e(AnkiDroidApp.TAG, "Evernote: Tried to call logout with not logged in", e);
		}
	}

	public void sync(){
		
		if (!mEvernoteSession.isLoggedIn()) {
			login();
			return;
		};
		
		prefs = context.getSharedPreferences(AnkiDroidApp.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
		LastSync = prefs.getLong("evernoteSync_LastSync", new Date().getTime());
		eSearchString = prefs.getString("evernoteSync_SearchString","");
		mCol = AnkiDroidApp.getCol();
		
		checkRequirements();

		NoteFilter filter = new NoteFilter();
		filter.setAscending(true);
		filter.setWords(eSearchString);
		int maxNotes = 999;
		int offset = 0;
		try {
			eNoteStore = mEvernoteSession.getClientFactory().createNoteStoreClient();
			eNoteStore.findNotes(filter, offset, maxNotes, new OnClientCallback<NoteList>() {

				@Override
				public void onSuccess(NoteList data) {
					syncAsyncTask task = new syncAsyncTask();
					task.execute(data);
				}

				@Override
				public void onException(Exception exception) {
					Log.e(AnkiDroidApp.TAG, "Evernote: " + exception.getMessage());
				}
			});


		} catch (TTransportException exception) {
			Log. e(AnkiDroidApp.TAG, "Evernote: " + exception.getMessage());
		}
	}

	private class syncAsyncTask extends AsyncTask<NoteList, Integer, String> {
		
		@Override
        public void onPreExecute() {
           progressDialog = StyledProgressDialog.show(context, "Evernote Sync","Preparing Sync", true);
        }
		
		@Override
        public void onProgressUpdate(Integer... counter) {
        	progressDialog.setMessage("Syncing Evernote note " + counter[0].toString() + " of " + eTotalNotes + "...");
         }
		
		
		@Override
		protected String doInBackground(NoteList... data) {
			eTotalNotes = data[0].getTotalNotes();
			Integer counter = 0;
			this.publishProgress(counter++);
			Log.i(AnkiDroidApp.TAG, "Evernote: count of found notes is " + eTotalNotes);
			for (com.evernote.edam.type.Note eNote : data[0].getNotes()) {
				String guid = eNote.getGuid();
				eNotes.add(guid);
				//delete_AppDataEntry(note);
				if (!eNote.getAttributes().isSetApplicationData() || !eNote.getAttributes().getApplicationData().isSetKeysOnly()) {
					create_aNote(eNote);
				} else {
					Set<String> eAppData = eNote.getAttributes().getApplicationData().getKeysOnly();
					if(eAppData.contains(ankiID_s)) {
						String appDataEntry = "0";
						String token = mEvernoteSession.getAuthToken();
						try {
							appDataEntry = eNoteStore.getClient().getNoteApplicationDataEntry(token, guid, ankiID_s);
						} catch (EDAMUserException e) {
							e.printStackTrace();
						} catch (EDAMSystemException e) {
							e.printStackTrace();
						} catch (EDAMNotFoundException e) {
							e.printStackTrace();
						} catch (TException e) {
							e.printStackTrace();
						}

						long ankiID = 1;
						try {
							ankiID = Long.parseLong(appDataEntry);
						} catch (NumberFormatException e) {
							e.printStackTrace();
						}

						Note aNote = new Note(mCol ,ankiID);
						if (aNote.getMid() == 0) {
							create_aNote(eNote);
							continue;
						}

						if(LastSync < eNote.getUpdated()){
							String title = eNote.getTitle();
							aNote.setitem("title", title);
							aNote.setitem("guid", guid);
							aNote.flush();
							Log.i(AnkiDroidApp.TAG, "Evernote: anki note of " + title + " got updated");
						}
					} else {
						create_aNote(eNote);
					}
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			progressDialog.dismiss();
			finish();
		}

	}

	private void finish() {
			delete_aNotes();
			set_last_sync_time();
			//Toast.makeText( this.context , "Evernote: Sync finished", Toast. LENGTH_SHORT).show();
			Log.i(AnkiDroidApp.TAG, "Evernote: Sync finished");
	}

	private void set_last_sync_time() {
		Date date = new Date(System.currentTimeMillis());
		long millis = date.getTime();
		SharedPreferences.Editor editor = prefs.edit();
		editor.putLong("evernoteSync_LastSync" , millis);
		editor.commit();
	}

	private void delete_aNotes() {
		java.util.ArrayList<Long> s = new java.util.ArrayList<Long>();
		long did = 0L;
		try {
			did = mCol.getDecks().byName("Evernote").getLong("id");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		long [] aCards = mCol.getDecks().cids(did);

		for (long i : aCards) {
			long Nid = mCol.getCard(i).getNid();
			String[] Nfields = mCol.getNote(Nid).getFields();
			if (!eNotes.contains(Nfields[1])) {
				s.add(Nid);
			}
		}

		long[] to_delete = new long[s.size()];
		for (int i = 0; i < s.size(); i++)
			to_delete[i] = s.get(i);
		mCol.remNotes(to_delete);
	}

	private void create_aNote(com.evernote.edam.type.Note note) {
		JSONObject model = mCol.getModels().byName("Evernote");
		Note aNote = new Note(mCol , model);
		long aNoteDid = 0;
		
		aNote.setitem("title", note.getTitle());
		aNote.setitem("guid" , note.getGuid());
		try {
			aNoteDid = mCol.getDecks().byName("Evernote").getLong("id");
		} catch (JSONException e) {
			e.printStackTrace();
		}
		try {
			aNote.model().put("did", aNoteDid);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		mCol.addNote(aNote);
		String guid = note.getGuid();
		String adid = Long.toString(aNote.getId());
		try {
			eNoteStore.getClient().setNoteApplicationDataEntry(mEvernoteSession.getAuthToken(), guid, ankiID_s, adid);
		} catch (EDAMUserException e) {
			e.printStackTrace();
			return;
		} catch (EDAMSystemException e) {
			e.printStackTrace();
			return;
		} catch (EDAMNotFoundException e) {
			e.printStackTrace();
			return;
		} catch (TException e) {
			e.printStackTrace();
			return;
		}
		Log.i(AnkiDroidApp. TAG, "Evernote: " + note.getTitle() + " - anki note ID appended");
	}

	
	public static class updateUsername extends AsyncTask<Void, String, String> {
		private Context mContext;
		public updateUsername(Context ctx){
			mContext = ctx;
		}

		@Override
		protected String doInBackground(Void... params) {
			String username = "unknown";
			try {
				username = mEvernoteSession.getClientFactory().createUserStoreClient().getClient().getUser(mEvernoteSession.getAuthToken()).getName();
			} catch (IllegalStateException e) {
				e.printStackTrace();
			} catch (TTransportException e) {
				e.printStackTrace();
			} catch (EDAMUserException e) {
				e.printStackTrace();
			} catch (EDAMSystemException e) {
				e.printStackTrace();
			} catch (TException e) {
				e.printStackTrace();
			}
			return username;
		}

		@Override
		protected void onPostExecute(String username) {
			SharedPreferences prefs = mContext.getSharedPreferences(AnkiDroidApp.SHARED_PREFS_NAME, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("evernoteSync_Username", username);
			editor.commit();
		}
	}
	

	public void checkRequirements() {
		if (mCol.getDecks().byName("Evernote").isNull("id")){
			mCol.getDecks().id("Evernote", true);
		}
		if (mCol.getModels().byName("Evernote").isNull("did")){
			JSONObject m = mCol.getModels().newModel("Evernote");
			mCol.getModels().addField(m, mCol.getModels().newField("title"));
			mCol.getModels().addField(m, mCol.getModels().newField("guid"));			
			JSONObject t = mCol.getModels().newTemplate("Card");
			try {
				t.put("qfmt", "{{title}}");
				t.put("afmt", "");
			} catch (JSONException e) {
				throw new RuntimeException(e);
			}
			mCol.getModels().addTemplate(m, t);
			mCol.getModels().add(m);
		}
	}

	private void delete_AppDataEntry(com.evernote.edam.type.Note note) {
		try {
			eNoteStore.getClient().setNoteApplicationDataEntry(mEvernoteSession.getAuthToken(), note.getGuid(), ankiID_s, "1");
		} catch (EDAMUserException e) {
			e.printStackTrace();
		} catch (EDAMSystemException e) {
			e.printStackTrace();
		} catch (EDAMNotFoundException e) {
			e.printStackTrace();
		} catch (TException e) {
			e.printStackTrace();
		}	
	}
	
	private boolean EvernoteAppInstalled(String uri)
    {
        PackageManager pm = context.getPackageManager();
        boolean app_installed = false;
        try
        {
               pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
               app_installed = true;
        }
        catch (PackageManager.NameNotFoundException e)
        {
               app_installed = false;
        }
        return app_installed ;
}

}