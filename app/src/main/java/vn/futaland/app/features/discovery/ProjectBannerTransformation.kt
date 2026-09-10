package vn.futaland.app.features.discovery

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/** Removes translucent export shadow/transparent padding from CMS project artwork only. */
class ProjectBannerTransformation : Transformation() {
    override val cacheKey: String = "project-banner-alpha-trim-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (!input.hasAlpha()) return input
        var left = input.width
        var top = input.height
        var right = -1
        var bottom = -1
        val row = IntArray(input.width)
        for (y in 0 until input.height) {
            input.getPixels(row, 0, input.width, 0, y, input.width, 1)
            for (x in 0 until input.width) {
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
            bottom == input.height - 1
        ) {
            return input
        }
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}
