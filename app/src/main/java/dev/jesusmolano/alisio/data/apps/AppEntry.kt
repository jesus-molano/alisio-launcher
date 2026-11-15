package dev.jesusmolano.alisio.data.apps

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.UserHandle

/** @param updatedAt cuándo se instaló o actualizó por última vez; 0 si no se ha podido saber. */
class AppEntry(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: Bitmap,
    val updatedAt: Long
)
