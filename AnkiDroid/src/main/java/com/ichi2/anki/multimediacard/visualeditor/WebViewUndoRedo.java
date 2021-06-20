/*
 Copyright (c) 2021 Akshay Jadhav <jadhavAkshay0701@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.ichi2.anki.multimediacard.visualeditor;


import com.ichi2.anki.multimediacard.activity.VisualEditorActivity;

import java.util.LinkedList;

/**
 * A generic undo/redo implementation for Visual Editor.
 *
 * About WebViewUndoRedo Implementation
 *
 * I used LinkedList data structure to store all changes of editor in a linear way,
 * and maintained pointer which will point to the current version of the editor,
 * user can use undo/redo by simply incrementing pointer or decrementing,
 * for now, I set the max size of LinkedList history 20.
 *
 * THIS CLASS IS PROVIDED TO THE PUBLIC DOMAIN FOR FREE WITHOUT ANY
 * RESTRICTIONS OR ANY WARRANTY.
 *
 * reference: https://www.programmersought.com/article/61722948894/
 *
 */
public class WebViewUndoRedo {

    /**
     * Is undo/redo being performed? This member signals if an undo/redo
     * operation is currently being performed. Changes in the editor during
     * undo/redo are not recorded because it would mess up the undo history.
     */
    private boolean mIsUndoOrRedo = false;

    /**
     * The edit history.
     */
    private final EditHistory mEditHistory;

    /**
     * The main content holders.
     */
    private final VisualEditorWebView mVisualEditorWebView;
    private final VisualEditorActivity mVisualEditorActivity;

    // =================================================================== //

    /**
     * Create a new WebViewUndoRedo.
     *
     * @param visualEditorWebView
     * undo/redo is implemented.
     */
    public WebViewUndoRedo(VisualEditorActivity visualEditorActivity, VisualEditorWebView visualEditorWebView) {

        mVisualEditorWebView = visualEditorWebView;
        mVisualEditorActivity = visualEditorActivity;

        //set previous content
        setContent(visualEditorActivity.getCurrentText());
        mEditHistory = new EditHistory();
        setMaxHistorySize(20);

        // listener
        mVisualEditorWebView.setOnTextChangeListener(this::onTextChanged);
    }


    // =================================================================== //

    /**
     * Set the maximum history size. If size is negative, then history size is
     * only limited by the device memory.
     */
    public void setMaxHistorySize(int maxHistorySize) {
        mEditHistory.setMaxHistorySize(maxHistorySize);
    }

    /**
     * Clear history.
     */
    public void clearHistory() {
        mEditHistory.clear();
    }

    /**
     * Can undo be performed?
     */
    public boolean getCanUndo() {
        return (mEditHistory.mMPosition > 0);
    }

    /**
     * Perform undo.
     */
    public void undo() {
        if (!getCanUndo()) {
            return;
        }
        String edit = mEditHistory.getPrevious();
        setContent(edit);
    }

    /**
     * Can redo be performed?
     */
    public boolean getCanRedo() {
        return (mEditHistory.mMPosition < mEditHistory.mMHistory.size());
    }

    /**
     * Perform redo.
     */
    public void redo() {
        if (!getCanRedo()) {
            return;
        }
        String edit = mEditHistory.getNext();
        setContent(edit);
    }


    public void setContent(String edit) {
        if (edit == null) {
            return;
        }
        mIsUndoOrRedo = true;
        mVisualEditorWebView.setHtml(edit);
        mVisualEditorWebView.load();
        mVisualEditorActivity.setCurrentText(edit);
    }


    private void onTextChanged(String s) {
        if (!mIsUndoOrRedo) {
            mEditHistory.add(s);
        }
        mIsUndoOrRedo = false;
        mVisualEditorActivity.invalidateOptionsMenu();
    }

    // =================================================================== //

    /**
     * Keeps track of all the edit history of a text.
     */
    @SuppressWarnings("InnerClassMayBeStatic")
    private final class EditHistory {

        /**
         * The position from which an EditItem will be retrieved when getNext()
         * is called. If getPrevious() has not been called, this has the same
         * value as mmHistory.size().
         */
        private int mMPosition = 0;

        /**
         * Maximum undo history size.
         */
        private int mMMaxHistorySize = -1;

        /**
         * The list of edits in chronological order.
         */
        private final LinkedList<String> mMHistory = new LinkedList<String>();

        /**
         * Clear history.
         */
        private void clear() {
            mMPosition = 0;
            mMHistory.clear();
        }

        /**
         * Adds a new edit operation to the history at the current position. If
         * executed after a call to getPrevious() removes all the future history
         * (elements with positions >= current history position).
         */
        private void add(String item) {
            mMHistory.add(item);
            mMPosition++;

            if (mMMaxHistorySize >= 0) {
                trimHistory();
            }
            mVisualEditorActivity.setCurrentText(item);
        }

        /**
         * Set the maximum history size. If size is negative, then history size
         * is only limited by the device memory.
         */
        private void setMaxHistorySize(int maxHistorySize) {
            mMMaxHistorySize = maxHistorySize;
            if (mMMaxHistorySize >= 0) {
                trimHistory();
            }
        }

        /**
         * Trim history when it exceeds max history size.
         */
        private void trimHistory() {
            while (mMHistory.size() > mMMaxHistorySize) {
                mMHistory.removeFirst();
                mMPosition--;
            }

            if (mMPosition < 0) {
                mMPosition = 0;
            }
        }

        /**
         * Traverses the history backward by one position, returns and item at
         * that position.
         */
        private String getPrevious() {
            if (mMPosition == 0) {
                return null;
            }
            mMPosition = mMPosition - 1;
            return mMHistory.get(mMPosition);
        }

        /**
         * Traverses the history forward by one position, returns and item at
         * that position.
         */
        private String getNext() {
            if (mMPosition >= mMHistory.size() - 1) {
                return null;
            }
            mMPosition = mMPosition + 1;
            return mMHistory.get(mMPosition);
        }
    }

}