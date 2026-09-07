package com.shuddhatype.ime

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Hands the rendered sticker to whichever app is being typed into.
 *
 * A keyboard cannot pass a bitmap directly — `commitContent` takes a URI, and
 * the receiving app opens it in its own process. Something has to answer that
 * open, so this does. Written by hand because the project carries no AndroidX
 * and `FileProvider` is not worth the dependency for one file.
 *
 * It is deliberately as small as a provider can be: it serves read-only from
 * one directory inside the app's own cache, and does nothing at startup. The
 * project avoids libraries that install providers running before
 * Application.onCreate(); this one is exported but inert until asked.
 */
class StickerProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = resolve(uri) ?: return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    /**
     * Some apps ask for the name and size before opening. Answering keeps the
     * picture from arriving as "unknown file".
     */
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val f = resolve(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = MatrixCursor(cols)
        cursor.addRow(cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }.toTypedArray())
        return cursor
    }

    /**
     * Only ever the one directory, and only a plain file name. A caller that
     * asks for `../../databases/something` gets nothing.
     */
    private fun resolve(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || name.contains("..")) return null
        val f = File(dir(context!!), name)
        return if (f.isFile) f else null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
    ): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.shuddhatype.stickers"

        /** Inside the cache: the picture has been sent, keeping it is pointless. */
        fun dir(context: android.content.Context): File =
            File(context.cacheDir, "stickers").apply { mkdirs() }

        fun uriFor(name: String): Uri =
            Uri.parse("content://$AUTHORITY/$name")
    }
}
