package com.scaso.drclawapp.data.model

/**
 * Platform-neutral icon identifier for a slash command. The UI layer maps this
 * to a concrete Compose ImageVector (see SlashCommandPopup) — the domain model
 * stays free of Android/Compose types so it can move to a KMP module.
 */
enum class SlashIcon {
    ADD,
    DELETE,
    INFO,
    BUILD,
}

data class SlashCommand(
    val name: String,
    val description: String,
    val icon: SlashIcon,
)

/**
 * Built-in slash commands available in every chat session.
 */
val BuiltInSlashCommands: List<SlashCommand> = listOf(
    SlashCommand(
        name = "/new",
        description = "New chat session",
        icon = SlashIcon.ADD,
    ),
    SlashCommand(
        name = "/clear",
        description = "Clear history",
        icon = SlashIcon.DELETE,
    ),
    SlashCommand(
        name = "/help",
        description = "Show help",
        icon = SlashIcon.INFO,
    ),
)
