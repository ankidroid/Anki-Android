/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.withStyledAttributes
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.R
import com.ichi2.preferences.usingStyledAttributes
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.Permissions

/**
 * Layout item that can be used to get a permission from the user.
 *
 * XML attributes:
 * * app:permissionTitle ([R.styleable.PermissionItem_permissionTitle]):
 *     Title of the permission
 * * app:permissionSummary ([R.styleable.PermissionItem_permissionSummary]):
 *     Brief description of the permission. It can be used to explain to the user
 *     why the permission should be granted
 * * app:permissionIcon ([R.styleable.PermissionItem_permissionIcon]):
 *     Icon to be shown at the frame side
 * * app:permission ([R.styleable.PermissionItem_permission]):
 *     Permission string to be asked. Will be overridden by app:permissions if set.
 * * app:permissions ([R.styleable.PermissionItem_permissions]):
 *     Array of permission strings to be asked. Overrides app:permission if set
 *
 * @see R.layout.permission_item
 */
class PermissionItem(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    private val switch: SwitchCompat
    val permissions: List<String>
    val isGranted get() = Permissions.hasAllPermissions(context, permissions)

    init {
        LayoutInflater.from(context).inflate(R.layout.permission_item, this, true)

        switch =
            findViewById<SwitchCompat>(R.id.switch_widget).apply {
                isEnabled = true
                setOnCheckedChangeListener { button, _ ->
                    button.isChecked = isGranted
                }
            }

        permissions =
            context.usingStyledAttributes(attrs, R.styleable.PermissionItem) {
                getTextArray(R.styleable.PermissionItem_permissions)?.map { it.toString() }
                    ?: getString(R.styleable.PermissionItem_permission)?.let { listOf(it) }
                    ?: throw IllegalArgumentException("Either app:permission or app:permissions should be set")
            }

        context.withStyledAttributes(attrs, R.styleable.PermissionItem) {
            findViewById<FixedTextView>(R.id.title).text = getText(R.styleable.PermissionItem_permissionTitle)
            findViewById<FixedTextView>(R.id.summary).text = getText(R.styleable.PermissionItem_permissionSummary)

            val icon = getDrawable(R.styleable.PermissionItem_permissionIcon)
            icon?.let {
                val color = MaterialColors.getColor(this@PermissionItem, android.R.attr.colorControlNormal)
                findViewById<ImageView>(R.id.icon).apply {
                    setImageDrawable(it)
                    imageTintList = ColorStateList.valueOf(color)
                }
            }
        }
    }

    /**
     * Checks the switch if the permission is granted,
     * or uncheck if not
     */
    fun updateSwitchCheckedStatus() {
        switch.isChecked = isGranted
    }

    /**
     * It should be use to request the permission.
     * The listener isn't invoked if the permission is already granted
     * */
    fun setOnSwitchClickListener(listener: () -> Unit) {
        switch.setOnClickListener {
            if (!isGranted) {
                listener.invoke()
            }
        }
    }
}
