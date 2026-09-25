package vn.futaland.app.features.discovery

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** Removes translucent export shadow/transparent padding from CMS project artwork only. */
class ProjectBannerTransformation : Transformation() {
    override val cacheKey: String = "project-banner-alpha-trim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        // Coil decodes network images as HARDWARE on API 26+, and getPixels()
        // throws on them — copy to a software ARGB_8888 bitmap first.
        val source = if (input.config == Bitmap.Config.HARDWARE) {
            input.copy(Bitmap.Config.ARGB_8888, false) ?: input
        } else {
            input
        }
        var left = source.width
        var top = source.height
        var right = -1
        var bottom = -1
        val row = IntArray(source.width)
        for (y in 0 until source.height) {
            source.getPixels(row, 0, source.width, 0, y, source.width, 1)
            for (x in 0 until source.width) {
                if (row[x] ushr 24 >= 250) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (
            right < left ||
            bottom < top ||
            top == 0 ||
            bottom == source.height - 1
        ) {
            return source
        }
        return Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
    }
}
