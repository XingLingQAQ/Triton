package com.rexcantor64.triton.player;

import com.rexcantor64.triton.Triton;
import com.rexcantor64.triton.api.language.Language;
import com.rexcantor64.triton.language.ExecutableCommand;
import com.rexcantor64.triton.utils.SocketUtils;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.val;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

public class VelocityLanguagePlayer implements LanguagePlayer {
    private final Player parent;

    private Language language;

    private String lastTabHeader;
    private String lastTabFooter;
    private HashMap<UUID, String> bossBars = new HashMap<>();
    private boolean waitingForClientLocale = false;

    public VelocityLanguagePlayer(Player parent) {
        this.parent = parent;
        Triton.get().runAsync(this::load);
    }

    public static VelocityLanguagePlayer fromUUID(UUID uuid) {
        val player = Triton.asVelocity().getLoader().getServer().getPlayer(uuid);
        return player.map(VelocityLanguagePlayer::new).orElse(null);
    }

    public void setBossbar(UUID uuid, String lastBossBar) {
        bossBars.put(uuid, lastBossBar);
    }

    public void removeBossbar(UUID uuid) {
        bossBars.remove(uuid);
    }

    public void setLastTabHeader(String lastTabHeader) {
        this.lastTabHeader = lastTabHeader;
    }

    public void setLastTabFooter(String lastTabFooter) {
        this.lastTabFooter = lastTabFooter;
    }

    @Override
    public boolean isWaitingForClientLocale() {
        return waitingForClientLocale;
    }

    @Override
    public void waitForClientLocale() {
        this.waitingForClientLocale = true;
    }

    public Language getLang() {
        if (language == null)
            language = Triton.get().getLanguageManager().getMainLanguage();
        return language;
    }

    public void setLang(Language language) {
        setLang(language, true);
    }

    public void setLang(Language language, boolean sendToSpigot) {
        // TODO fire Triton's API change language event
        this.language = language;
        if (this.waitingForClientLocale && getParent() != null)
            parent.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(Triton.get().getMessagesConfig()
                    .getMessage("success.detected-language", language.getDisplayName())));
        this.waitingForClientLocale = false;

        if (sendToSpigot && getParent() != null)
            Triton.asVelocity().getBridgeManager().sendPlayerLanguage(this);

        save();
        refreshAll();
        executeCommands(null);
    }

    public void refreshAll() {
        // TODO
    }

    @Override
    public UUID getUUID() {
        return this.parent.getUniqueId();
    }

    public Player getParent() {
        return parent;
    }

    private void load() {
        this.language = Triton.get().getStorage().getLanguage(this);
        Triton.get().getStorage()
                .setLanguage(null, SocketUtils.getIpAddress(getParent().getRemoteAddress()), language);
    }

    private void save() {
        Triton.get().runAsync(() -> {
            val ip = SocketUtils.getIpAddress(getParent().getRemoteAddress());
            Triton.get().getStorage().setLanguage(getParent().getUniqueId(), ip, language);
        });
    }

    public void executeCommands(RegisteredServer overrideServer) {
        val currentServer = getParent().getCurrentServer();
        if (overrideServer == null && !currentServer.isPresent()) return;
        val server = overrideServer == null ? currentServer.get().getServer() : overrideServer;
        for (val cmd : ((com.rexcantor64.triton.language.Language) language).getCmds()) {
            val cmdText = cmd.getCmd().replace("%player%", getParent().getUsername())
                    .replace("%uuid%", getParent().getUniqueId().toString());

            if (!cmd.isUniversal() && !cmd.getServers().contains(server.getServerInfo().getName()))
                continue;

            val velocity = Triton.asVelocity().getLoader().getServer();

            if (cmd.getType() == ExecutableCommand.Type.SERVER)
                Triton.asVelocity().getBridgeManager().sendExecutableCommand(cmdText, server);
            else if (cmd.getType() == ExecutableCommand.Type.PLAYER)
                getParent().spoofChatInput("/" + cmdText);
            else if (cmd.getType() == ExecutableCommand.Type.BUNGEE)
                velocity.getCommandManager().executeAsync(velocity.getConsoleCommandSource(), cmdText);
            else if (cmd.getType() == ExecutableCommand.Type.BUNGEE_PLAYER)
                velocity.getCommandManager().executeAsync(getParent(), cmdText);
        }
    }

    @Override
    public String toString() {
        return "VelocityLanguagePlayer{" +
                "username=" + parent.getUsername() +
                ", uuid=" + parent.getUniqueId() +
                ", language=" + Optional.ofNullable(language).map(Language::getName).orElse("null") +
                '}';
    }
}
