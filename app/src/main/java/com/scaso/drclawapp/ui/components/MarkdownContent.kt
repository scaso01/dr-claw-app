package com.scaso.drclawapp.ui.components

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock

/**
 * Renders markdown content using the multiplatform-markdown-renderer-m3 library.
 *
 * Features:
 * - Full Markdown rendering (headers, bold, italic, lists, links, etc.)
 * - Code blocks with a copy-to-clipboard button at the top-right corner
 *
 * Use this only for finalized (non-streaming) assistant messages. Partial markdown
 * with unclosed blocks will break the renderer.
 */
@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Markdown(
        content = content,
        modifier = modifier,
        components = markdownComponents(
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node) { code, _ ->
                    CodeBlockWithCopyButton(
                        code = code,
                        onCopy = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Dr. CLAW", code))) }
                        },
                    )
                }
            },
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node) { code, _ ->
                    CodeBlockWithCopyButton(
                        code = code,
                        onCopy = {
                            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Dr. CLAW", code))) }
                        },
                    )
                }
            },
        ),
    )
}

/**
 * A code block surface with a copy button pinned to the top-right corner.
 */
@Composable
private fun CodeBlockWithCopyButton(
    code: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 1.dp,
    ) {
        Box {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
                    .padding(end = 32.dp), // leave room for copy button
            )

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.padding(2.dp),
                )
            }
        }
    }
}
