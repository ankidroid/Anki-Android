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
package com.ichi2.anki.controller;

import java.lang.ref.WeakReference;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.PluginManager;
import com.ichi2.anki.R;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class ControllerManager {
	public static final int MSG_ZEEMOTE_BUTTON_BASE = 0x110;
	public static final int MSG_ZEEMOTE_STICK_BASE = 0x210;

	public static final int MSG_CNTRL_ACTION = 0;
	public static final int MSG_CNTRL_REGISTER_MANAGER = -1;
	public static final int MSG_CNTRL_WAIT_FOR_CONNECT = -2;
	public static final int MSG_CNTRL_SUPPORTED_ACTIONS = -4;
	public static final int MSG_CNTRL_START_LISTENING = -5;
	public static final int MSG_CNTRL_STOP_LISTENING = -6;
	public static final int MSG_CNTRL_DISCONNECT = -7;
	public static final int MSG_ARG_REQ_ACK = 0;
    public static final int MSG_ARG_ACK = 1;
    public static final int MSG_ARG_DONE = 2;
    
    private static ControllerManager sInstance;
    private PluginManager mPlm;
    private String mControllerKey = null;

    private static boolean mIsBound = false;
    private static boolean mIsConnected = false;
    private static boolean mIsConnecting = false;
    private final ControllerConnection mControllerConnection = new ControllerConnection();
    public final Messenger mFromController = new Messenger(new IncomingHandler(this));
    public static Messenger mToController;
	WeakReference<IAnkiControllable> mActivity;

    public ControllerManager() {
    	mPlm = AnkiDroidApp.getPluginManager();
    }

    static public ControllerManager getControllerManager() {
    	if (sInstance == null) {
    		sInstance = new ControllerManager();
    	}
    	return sInstance;
    }

    public void setReceiverActivity(IAnkiControllable client) {
    	if (mIsBound) {
	    	if (mIsConnecting) {
	    		if (mToController != null) {
		    		mIsConnecting = false;
		    		mIsConnected = true;
		    		Message msg = Message.obtain(null, MSG_CNTRL_WAIT_FOR_CONNECT);
		    		msg.arg1 = MSG_ARG_DONE;
		    		mActivity = new WeakReference<IAnkiControllable>(client);
					try {
						mToController.send(msg);
					} catch (RemoteException e) {
						Log.e(AnkiDroidApp.TAG, "Acknowledging controller service connection failed");
						closeConnection();
					}
	    		} else {
	    			Log.e(AnkiDroidApp.TAG, "Controller manager in inconsistent state");
	    			closeConnection();
	    		}
	    	} else if (mIsConnected) {
		    	mActivity = new WeakReference<IAnkiControllable>(client);
		    	Message msg = Message.obtain(null, MSG_CNTRL_START_LISTENING);
		    	try {
					mToController.send(msg);
				} catch (RemoteException e) {
					Log.e(AnkiDroidApp.TAG, "Registering controller listening activity (" + client.getClass().getName() + ") failed");
					closeConnection();
				}
	    	}
    	} else {
    		if (client.canStartController()) {
    			enableController();
    		}
    	}
    }

    public void unsetReceiverActivity() {
    	if (!mIsConnecting) {
	    	mActivity = null;
	    	if (mIsBound && mToController != null) {
		    	Message msg = Message.obtain(null, MSG_CNTRL_STOP_LISTENING);
		    	try {
					mToController.send(msg);
				} catch (RemoteException e) {
					Log.w(AnkiDroidApp.TAG, "Controller connection was found dead while closing.");
					closeConnection();
				}
	    	}
	    }
    }

    public void enableController() {
    	if (!mIsConnected && !mIsConnecting && !mIsBound) {
    		mControllerKey = mPlm.getEnabledControllerPlugin(AnkiDroidApp.getInstance().getBaseContext());
    		if (mControllerKey != null) {
    			Log.d(AnkiDroidApp.TAG, "Request enable " + mControllerKey);
    			Intent intent = mPlm.getPluginIntent(mControllerKey);
    			if (intent != null) {
    				AnkiDroidApp.getInstance().bindService(intent, mControllerConnection, Context.BIND_AUTO_CREATE);
    			}
    		}
    	}
    }

    public void disableController() {
		Log.d(AnkiDroidApp.TAG, "Request disable " + mControllerKey);
    	if (mIsBound && !mIsConnecting) {
    		closeConnection();
		}
    }
    
    private void closeConnection() {
		AnkiDroidApp.getInstance().unbindService(mControllerConnection);
		mIsConnected = false;
		mIsConnecting = false;
		mIsBound = false;
		mToController = null;
    }
    
    public void controllerPrefChanged(Context ctx, String controller, boolean newValue) {
    	String controllerStatus;
    	if (newValue == true) {
    		controllerStatus = "enabled";
    	} else {
    		controllerStatus = "disabled";
    	}
    	String toast = ctx.getResources().getString(R.string.controller_pref_changed_toast, controller, controllerStatus);
		Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show();
    }
    
    class ControllerConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mToController = new Messenger(service);
			mIsBound = true;
			try {			
				Message msg = Message.obtain(null, MSG_CNTRL_REGISTER_MANAGER);
				msg.replyTo = mFromController;
				mToController.send(msg);
				//msg = Message.obtain(null, MSG_CNTRL_SUPPORTED_ACTIONS);
				//msg.setData(getSupportedControllerActions());
				//mToController.send(msg);
				Log.d(AnkiDroidApp.TAG, "Connected controller service " + name);
			} catch (RemoteException e) {
				Log.e(AnkiDroidApp.TAG, "Binding controller service failed");
				closeConnection();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mIsConnected = false;
			mIsConnecting = false;
			mIsBound = false;
			mToController = null;
			Log.d(AnkiDroidApp.TAG, "Disconnected controller service " + name);
		}
    }

    static class IncomingHandler extends Handler {
    	private WeakReference<ControllerManager> mCntrlm;
    	
    	public IncomingHandler(ControllerManager owner) {
    		mCntrlm = new WeakReference<ControllerManager>(owner);
    	}
    	
    	@Override
    	public void handleMessage(Message msg) {
    		if (mIsBound) {
    			switch (msg.what) {
    			case MSG_CNTRL_WAIT_FOR_CONNECT:
    				if (msg.arg1 == MSG_ARG_REQ_ACK && !mIsConnecting) {
    					mIsConnecting = true;
    				}
    				Message response = Message.obtain(null, MSG_CNTRL_WAIT_FOR_CONNECT);
    				response.arg1 = MSG_ARG_ACK;
    				try {
						mToController.send(response);
					} catch (RemoteException e) {
						Log.e(AnkiDroidApp.TAG, "Negotiating controller connection failed");
						mCntrlm.get().closeConnection();
					}
    				break;
    			case MSG_CNTRL_DISCONNECT:
    				// User refused to connect, close connection
    				mCntrlm.get().closeConnection();
    				mIsConnecting = false;
    				break;
    			default:
    				if (mCntrlm.get().mActivity != null && mCntrlm.get().mActivity.get() != null) {
    					mCntrlm.get().mActivity.get().handleControllerMessage(msg);
    				}
    			}
    		}
        	super.handleMessage(msg);
    	}
    }
}
