package com.scaso.drclawapp.ui.components

import com.scaso.drclawapp.data.model.Message
import com.scaso.drclawapp.data.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles the "Edit Last Sent Message" workflow (Phase 8B).
 *
 * When a user selects "Edit" on their own message from the MessageActionSheet:
 * 1. The message content is loaded into the input field.
 * 2. The original message is marked for removal from the list.
 * 3. The user can modify and re-send the edited version.
 *
 * This class is designed to be owned by the ChatViewModel or composed
 * alongside it. It does not directly modify the repository -- instead it
 * exposes state that the ViewModel reads and acts on.
 *
 * No Android imports -- KMP-extractable.
 */
class EditMessageHandler {

    private val _editingMessage = MutableStateFlow<Message?>(null)

    /**
     * The message currently being edited, or null if not in edit mode.
     */
    val editingMessage: StateFlow<Message?> = _editingMessage.asStateFlow()

    /**
     * Whether the user is currently editing a message.
     */
    val isEditing: Boolean
        get() = _editingMessage.value != null

    /**
     * Begins editing a message. Only user messages can be edited.
     *
     * @param message The message to edit.
     * @return The message content to load into the input field,
     *         or null if the message is not editable.
     */
    fun startEditing(message: Message): String? {
        if (message.role != Role.USER) return null
        _editingMessage.value = message
        return message.content
    }

    /**
     * Cancels the current edit operation without modifying anything.
     */
    fun cancelEditing() {
        _editingMessage.value = null
    }

    /**
     * Completes the edit. Returns the ID of the message that should be
     * removed from the message list. The caller is responsible for
     * removing the message and sending the new text.
     *
     * @return The ID of the original message to remove, or null if not editing.
     */
    fun confirmEdit(): String? {
        val message = _editingMessage.value ?: return null
        _editingMessage.value = null
        return message.id
    }

    /**
     * Checks whether a given message is the one currently being edited.
     */
    fun isMessageBeingEdited(messageId: String): Boolean {
        return _editingMessage.value?.id == messageId
    }

    /**
     * Determines whether a message is eligible for editing.
     * Only user-sent, non-streaming messages can be edited.
     */
    fun canEdit(message: Message): Boolean {
        return message.role == Role.USER && !message.isStreaming
    }
}

/**
 * Result of an edit operation, used to communicate between
 * EditMessageHandler and the ViewModel/Repository.
 */
data class EditResult(
    /**
     * ID of the original message to remove from the list.
     */
    val originalMessageId: String,

    /**
     * The new text to send as a replacement.
     */
    val newContent: String,
)

/**
 * Extension function to create an [EditResult] from handler state.
 * Convenience for the ViewModel to call when the user hits send
 * while in edit mode.
 *
 * @param newContent The edited text from the input field.
 * @return An [EditResult] if currently editing, or null otherwise.
 */
fun EditMessageHandler.buildEditResult(newContent: String): EditResult? {
    val originalId = confirmEdit() ?: return null
    return EditResult(
        originalMessageId = originalId,
        newContent = newContent,
    )
}
