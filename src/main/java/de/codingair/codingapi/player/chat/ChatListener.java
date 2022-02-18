package de.codingair.codingapi.player.chat;

import de.codingair.codingapi.API;
import de.codingair.codingapi.player.data.PacketReader;
import de.codingair.codingapi.server.reflections.IReflection;
import de.codingair.codingapi.server.specification.Version;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

public class ChatListener implements Listener {
    private static final Class<?> chatPacket;
    private static final IReflection.FieldAccessor<String> text;

    static {
        chatPacket = IReflection.getClass(IReflection.ServerPacket.PACKETS, "PacketPlayInChat");
        text = IReflection.getField(chatPacket, Version.since(17, "a", "b"));
    }

    public ChatListener() {

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            inject(onlinePlayer);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        inject(e.getPlayer());
    }

    private void inject(Player player) {
        new PacketReader(player, "chat-button-listener", API.getInstance().getMainPlugin()) {
            @Override
            public boolean readPacket(Object packet) {
                if (packet.getClass().equals(chatPacket)) {
                    String msg = text.get(packet);

                    if (msg == null || !msg.startsWith(ChatButton.PREFIX)) return false;
                    String type = null;
                    UUID uniqueId;

                    if (msg.contains("#")) {
                        String[] a = msg.split("#");
                        uniqueId = UUID.fromString(a[0].replace(ChatButton.PREFIX, ""));
                        type = a[1];
                    } else uniqueId = UUID.fromString(msg.replace(ChatButton.PREFIX, ""));

                    List<SimpleMessage> messageList = API.getRemovables(null, SimpleMessage.class);
                    boolean used = handleSimpleMessages(type, uniqueId, player, messageList);

                    messageList = API.getRemovables(player, SimpleMessage.class);
                    used |= handleSimpleMessages(type, uniqueId, player, messageList);

                    return used;
                }

                return false;
            }

            @Override
            public boolean writePacket(Object packet) {
                return false;
            }
        }.inject();
    }

    private boolean handleSimpleMessages(String type, UUID uniqueId, Player player, List<SimpleMessage> messageList) {
        boolean clicked = false;

        if (!messageList.isEmpty()) {
            for (SimpleMessage message : messageList) {

                ChatButton button = message.getButton(uniqueId);
                if (button != null) {
                    clicked = true;

                    Bukkit.getScheduler().runTask(API.getInstance().getMainPlugin(), () -> {
                        if (button.canClick()) {
                            if (button.getSound() != null) button.getSound().play(player);
                            button.onClick(player);
                        }
                    });

                    break;
                }
            }

            messageList.clear();
        }

        if (clicked) return true;
        else return callForeignClick(player, uniqueId, type);
    }

    private boolean callForeignClick(Player player, UUID uniqueId, String type) {
        return ChatButtonManager.onAsyncInteract(l -> l.onAsyncClick(player, uniqueId, type));
    }
}
