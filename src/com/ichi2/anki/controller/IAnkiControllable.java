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

import android.os.Bundle;
import android.os.Message;

public interface IAnkiControllable {
	
    /**
     * Return a mapping of supported action names to integer codes.
     * The activity should define names for each supported controller action and
     * map each one to an integer code using a SparceArray<String> object.
     * Negative codes 0-10 are reserved and overridden by AnkiControllableActivity class.
     * @return A SparceArray that contains the names of each supported action
     */
    public abstract Bundle getSupportedControllerActions();

    /**
     * Custom handling of controller actions.
     * @param msg A message from the controller service describing the action.
     */
    public abstract void handleControllerMessage(Message msg);
    
    /**
     * Indicates whether this type of activity can initialise the controller connection on onResume
     * @return False by default, override to give controller termination permission to this Activity.
     */
    public boolean canStartController();
    
    /**
     * Indicates whether this type of activity can kill the controller connection on onDestroy
     * @return False by default, override to give controller termination permission to this Activity.
     */
    public boolean canStopController();
}
