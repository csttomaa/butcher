package com.butcher;

import com.butcher.mixin.ChatComponentInvoker;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayDeque;

/**
 * Chat lines held back while a cutscene or the profit window is up, and put back afterwards.
 *
 * <h2>Why held, and why here</h2>
 *
 * Chat is kept out of sight while a cutscene plays - a drop message landing mid-animation
 * gives the reveal away. The first version <b>dropped</b> those lines, and a Thaw with its
 * ending runs ten to twenty seconds, so whole conversations went missing. The second held
 * them at Fabric's receive events and put them back the moment the animation ended - which
 * was exactly when the profit window opened, so they faded out behind it (a chat line shows
 * for ten seconds) and the player still saw nothing. It also replayed lines other mods had
 * chosen to hide, since at that hook nobody can tell what the other listeners voted.
 *
 * So now lines are held at {@code ChatComponent.addMessage} (see
 * {@link com.butcher.mixin.ChatComponentMixin}), after every other mod has filtered them, and
 * released only once nothing is covering the chat any more: no cutscene, no pre-kill hide,
 * no profit window. They go back through the same method, source and tag untouched.
 */
public final class ChatHold {
    private ChatHold() {}

    private record Line(ChatComponent chat, Component message, MessageSignature signature,
                        GuiMessageSource source, GuiMessageTag tag) {}

    private static final ArrayDeque<Line> held = new ArrayDeque<>();
    /** More than a cutscene's worth of even a busy lobby; the oldest go first past it. */
    private static final int MAX = 300;
    /** Set while putting lines back, so they are not caught a second time. */
    private static boolean releasing;

    /** Called for every line about to enter chat. True if it was held and must not go in now. */
    public static boolean hold(ChatComponent chat, Component message, MessageSignature signature,
                               GuiMessageSource source, GuiMessageTag tag) {
        if (releasing || !ButcherClient.chatHeld()) return false;
        if (held.size() >= MAX) held.pollFirst();
        held.addLast(new Line(chat, message, signature, source, tag));
        return true;
    }

    /** Forgets every held line without putting it back - the player has left the server. */
    public static void clear() {
        held.clear();
    }

    public static boolean isEmpty() {
        return held.isEmpty();
    }

    /** Puts every held line into chat, oldest first. */
    public static void release() {
        if (held.isEmpty()) return;
        int n = held.size();
        releasing = true;
        try {
            while (!held.isEmpty()) {
                Line l = held.pollFirst();
                ((ChatComponentInvoker) l.chat()).butcher$addMessage(l.message(), l.signature(), l.source(), l.tag());
            }
        } finally {
            releasing = false;
        }
        ButcherClient.decisions.note("§7Put " + n + " chat line(s) back that arrived while chat was covered");
    }
}
