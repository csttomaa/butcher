package com.butcher.mixin;

import com.butcher.ChatHold;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds chat lines back while a cutscene or the profit window is up - see {@link ChatHold}.
 *
 * {@code addMessage} is the one method every line goes through on its way into the chat box:
 * server messages, player chat, and lines other mods add themselves. Hooking it here, rather
 * than at Fabric's receive events, means every other mod's filtering has already happened -
 * a line SkyHanni or Skyblocker hides never reaches this point, so it can never be replayed.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void butcher$holdDuringCutscene(Component message, MessageSignature signature,
                                            GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (ChatHold.hold((ChatComponent) (Object) this, message, signature, source, tag)) ci.cancel();
    }
}
