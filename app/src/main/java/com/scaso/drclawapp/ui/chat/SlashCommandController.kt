package com.scaso.drclawapp.ui.chat

import com.scaso.drclawapp.data.model.BuiltInSlashCommands
import com.scaso.drclawapp.data.model.SlashCommand
import com.scaso.drclawapp.data.model.SlashIcon
import com.scaso.drclawapp.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the server-driven slash command list (B1-T10) and command execution.
 * Plain class (not @HiltViewModel), constructed by [ChatViewModel] -- mirrors the
 * existing [com.scaso.drclawapp.ui.voice.VoiceConversationManager] pattern of a
 * plain helper class taking a repository + scope directly.
 */
class SlashCommandController(
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
) {
    // B1-T10: server-driven slash commands (/new always client-only, prepended)
    private val _availableCommands = MutableStateFlow<List<SlashCommand>>(BuiltInSlashCommands)
    val availableCommands: StateFlow<List<SlashCommand>> = _availableCommands.asStateFlow()

    /** Fetches server-side slash commands and prepends the client-only /new. */
    fun loadServerCommands() {
        scope.launch {
            val serverCommands = repository.listSlashCommands()
            val newCmd = BuiltInSlashCommands.first { it.name == "/new" }
            val mapped = serverCommands.map { spec ->
                SlashCommand(
                    name = "/${spec.name}",
                    description = spec.description,
                    icon = SlashIcon.BUILD,
                )
            }
            _availableCommands.value = listOf(newCmd) + mapped
        }
    }

    fun executeCommand(commandName: String) {
        val name = commandName.removePrefix("/")
        scope.launch {
            val result = repository.executeSlashCommand(name)
            val display = if (result.ok) result.output else "Error: ${result.output}"
            repository.addLocalAssistantMessage(display)
        }
    }
}
