///***************************************************************************************
// * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>                         *
// *                                                                                      *
// * This program is free software; you can redistribute it and/or modify it under        *
// * the terms of the GNU General Public License as published by the Free Software        *
// * Foundation; either version 3 of the License, or (at your option) any later           *
// * version.                                                                             *
// *                                                                                      *
// * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
// * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
// * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
// *                                                                                      *
// * You should have received a copy of the GNU General Public License along with         *
// * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
// ****************************************************************************************/
//
//package com.ichi2.widget;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.Comparator;
//
//import org.json.JSONException;
//import org.json.JSONObject;
//
//import com.ichi2.anki.AnkiActivity;
//import com.ichi2.anki.DeckPicker;
//import com.ichi2.anki.CardEditor.JSONNameComparator;
//import com.ichi2.anki2.R;
//import com.ichi2.libanki.Collection;
//import com.ichi2.themes.StyledDialog;
//import com.ichi2.themes.Themes;
//
//import android.app.Activity;
//import android.content.DialogInterface;
//import android.content.DialogInterface.OnClickListener;
//import android.content.DialogInterface.OnDismissListener;
//import android.content.res.Resources;
//import android.content.Intent;
//import android.os.Bundle;
//
//public class WidgetDialog extends AnkiActivity {
//
//    public static final String ACTION_SHOW_DECK_SELECTION_DIALOG = "org.ichi2.WidgetDialog.SHOWDECKSELECTIONDIALOG";
//    public static final String ACTION_SHOW_RESTRICTIONS_DIALOG = "org.ichi2.WidgetDialog.SHOWRESTRICTIONSDIALOG";
//
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        Themes.applyTheme(this, Themes.THEME_NO_THEME);
//        super.onCreate(savedInstanceState);
//        Intent intent = getIntent();
//        if (intent != null) {
//            String action = intent.getAction();
//            if (action != null) {
//                if (ACTION_SHOW_DECK_SELECTION_DIALOG.equals(action)) {
//                    Collection col = Collection.currentCollection();
//                    if (col == null) {
//                        return;
//                    }
//                    ArrayList<CharSequence> dialogDeckItems = new ArrayList<CharSequence>();
//                    // Use this array to know which ID is associated with each
//                    // Item(name)
//                    final ArrayList<Long> dialogDeckIds = new ArrayList<Long>();
//
//                    ArrayList<JSONObject> decks = col.getDecks().all();
//                    Collections.sort(decks, new JSONNameComparator());
//                    StyledDialog.Builder builder = new StyledDialog.Builder(this);
//                    builder.setTitle(R.string.deck);
//                    for (JSONObject d : decks) {
//                        try {
//                            dialogDeckItems.add(DeckPicker.readableDeckName(d.getString("name").split("::")));
//                            dialogDeckIds.add(d.getLong("id"));
//                        } catch (JSONException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
//                    // Convert to Array
//                    String[] items = new String[dialogDeckItems.size()];
//                    dialogDeckItems.toArray(items);
//
//                    builder.setItems(items, new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int item) {
//                            Intent newIntent = new Intent(WidgetDialog.this, AnkiDroidWidgetBig.UpdateService.class);
//                            newIntent.putExtra(DeckPicker.EXTRA_DECK_ID, dialogDeckIds.get(item));
//                            newIntent.setAction(AnkiDroidWidgetBig.UpdateService.ACTION_OPENDECK);
//                            startService(newIntent);
//                        }
//                    });
//                    builder.setOnDismissListener(new OnDismissListener() {
//
//                        @Override
//                        public void onDismiss(DialogInterface arg0) {
//                            WidgetDialog.this.finishWithoutAnimation();
//                        }
//
//                    });
//                    builder.show();
//                } else if (ACTION_SHOW_RESTRICTIONS_DIALOG.equals(action)) {
//                    Resources res = getResources();
//                    StyledDialog.Builder builder = new StyledDialog.Builder(this);
//                    builder.setTitle(res.getString(R.string.widget_big))
//                            .setMessage(R.string.widget_big_restrictions_dialog)
//                            .setOnDismissListener(new OnDismissListener() {
//
//                                @Override
//                                public void onDismiss(DialogInterface arg0) {
//                                    WidgetDialog.this.finishWithoutAnimation();
//                                }
//                            }).setPositiveButton(res.getString(R.string.ok), null);
//                    builder.show();
//                }
//            }
//        }
//    }
//
//    public class JSONNameComparator implements Comparator<JSONObject> {
//        @Override
//        public int compare(JSONObject lhs, JSONObject rhs) {
//            String[] o1;
//            String[] o2;
//            try {
//                o1 = lhs.getString("name").split("::");
//                o2 = rhs.getString("name").split("::");
//            } catch (JSONException e) {
//                throw new RuntimeException(e);
//            }
//            for (int i = 0; i < Math.min(o1.length, o2.length); i++) {
//                int result = o1[i].compareToIgnoreCase(o2[i]);
//                if (result != 0) {
//                    return result;
//                }
//            }
//            if (o1.length < o2.length) {
//                return -1;
//            } else if (o1.length > o2.length) {
//                return 1;
//            } else {
//                return 0;
//            }
//        }
//    }
//}
