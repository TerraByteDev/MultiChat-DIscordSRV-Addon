package com.loohp.multichatdiscordsrvaddon.command.subcommand;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import static com.loohp.multichatdiscordsrvaddon.MultiChatDiscordSrvAddon.plugin;

@Command("multichat")
public class MainCommand {

    @Command("info")
    @Permission(value = {"multichatdiscordsrv.info"})
    public void execute(
            CommandSender sender
    ) {
        plugin.sendMessage("<aqua>MultiChat DiscordSRV Addon, written by LOOHP, adapted by Skullians.", sender);
        plugin.sendMessage("<grey>You are running MultiChatDiscordSRVAddon version <yellow>" + plugin.getDescription().getVersion(), sender);
    }
}
