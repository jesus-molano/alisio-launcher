package dev.jesusmolano.alisio.data.apps

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.UserHandle

class AppEntry(val key: AppKey, val label: String, val component: ComponentName, val user: UserHandle, val icon: Bitmap)
