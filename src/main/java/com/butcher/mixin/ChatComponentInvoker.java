package com.butcher.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches ChatComponent's private {@code addMessage}, so a held line goes back in exactly as
 * it was going to - same source, same tag - rather than being re-sent as a new message.
 */
@Mixin(ChatComponent.class)
public interface ChatComponentInvoker {

    @Invoker("addMessage")
    void butcher$addMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag);
}
