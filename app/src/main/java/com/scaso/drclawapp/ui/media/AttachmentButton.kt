package com.scaso.drclawapp.ui.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * An attachment button that shows a popup menu with options to pick
 * a photo from the gallery or a file from the file system.
 *
 * Uses [ActivityResultContracts.PickVisualMedia] for photo selection
 * and [ActivityResultContracts.GetContent] for general file selection.
 *
 * @param onAttachmentSelected Called with the URI and MIME type when
 *   the user picks a file or photo.
 * @param modifier Modifier for the root composable.
 */
@Composable
fun AttachmentButton(
    onAttachmentSelected: (uri: Uri, mimeType: String) -> Unit,
    onCameraClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Photo picker -- uses the modern photo picker API
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            val resolvedMimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            onAttachmentSelected(uri, resolvedMimeType)
        }
    }

    // File picker -- accepts any file type
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val resolvedMimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onAttachmentSelected(uri, resolvedMimeType)
        }
    }

    Box(modifier = modifier) {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "Attach file",
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Photo Library") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
            )

            DropdownMenuItem(
                text = { Text("File") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    filePickerLauncher.launch("*/*")
                },
            )

            DropdownMenuItem(
                text = { Text("Camera") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                    )
                },
                onClick = {
                    showMenu = false
                    onCameraClick()
                },
            )
        }
    }
}
