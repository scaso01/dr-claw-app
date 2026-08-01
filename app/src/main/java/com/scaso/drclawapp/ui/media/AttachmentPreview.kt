package com.scaso.drclawapp.ui.media

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scaso.drclawapp.data.model.Attachment

/**
 * A horizontal scrolling strip showing thumbnail previews of pending attachments.
 *
 * Each thumbnail displays either an image preview (loaded via [BitmapFactory]
 * from the content URI) or a generic file icon for non-image types.
 * An X button on each thumbnail allows removal.
 *
 * @param attachments The list of pending attachments to display.
 * @param onRemove Called with the attachment ID when the user taps the remove button.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun AttachmentPreview(
    attachments: List<Attachment>,
    onRemove: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = attachments,
            key = { it.id },
        ) { attachment ->
            AttachmentThumbnail(
                attachment = attachment,
                onRemove = { onRemove(attachment.id) },
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    attachment: Attachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isImage = attachment.mimeType.startsWith("image/")

    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (isImage) {
            ImageThumbnail(attachment = attachment)
        } else {
            FileThumbnail(attachment = attachment)
        }

        // Remove button (top-end corner)
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove ${attachment.displayName}",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ImageThumbnail(
    attachment: Attachment,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(attachment.uri) {
        try {
            context.contentResolver.openInputStream(attachment.uri)?.use { stream ->
                // Decode with inSampleSize for memory efficiency
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                // Calculate sample size for ~144px target (2x the 72dp thumbnail)
                val targetSize = 144
                var sampleSize = 1
                val width = options.outWidth
                val height = options.outHeight
                if (width > targetSize || height > targetSize) {
                    val halfWidth = width / 2
                    val halfHeight = height / 2
                    while ((halfWidth / sampleSize) >= targetSize &&
                        (halfHeight / sampleSize) >= targetSize
                    ) {
                        sampleSize *= 2
                    }
                }

                // Re-open stream and decode with sample size
                context.contentResolver.openInputStream(attachment.uri)?.use { stream2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeStream(stream2, null, decodeOptions)
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = attachment.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        // Fallback if bitmap decode fails
        FileThumbnail(attachment = attachment, modifier = modifier)
    }
}

@Composable
private fun FileThumbnail(
    attachment: Attachment,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
