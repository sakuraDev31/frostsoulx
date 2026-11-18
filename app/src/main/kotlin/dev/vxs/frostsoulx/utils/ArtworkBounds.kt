package dev.vxs.frostsoulx.utils

/** Bounds memory before allocation, including extremely wide/tall or malformed images. */
internal fun artworkSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    require(width > 0 && height > 0 && maxDimension > 0) { "Invalid artwork dimensions" }
    require(width.toLong() * height <= 256_000_000L) { "Artwork dimensions exceed pixel limit" }
    var sample = 1
    while ((width.toLong() + sample - 1) / sample > maxDimension ||
        (height.toLong() + sample - 1) / sample > maxDimension) {
        sample *= 2
    }
    return sample
}
