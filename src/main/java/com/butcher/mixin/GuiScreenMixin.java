package com.butcher.mixin;

import com.butcher.ButcherClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the chat box shut while a cutscene plays or the Scatha's pre-kill hide is up - opening it
 * would show the chat history, which is where a drop gives itself away. SkyMaxx does the same
 * ({@code chatShut}).
 *
 * This used to refuse every screen while a cutscene owned the screen, because the cutscenes were
 * Screens and anything opening over them replaced them. They are drawn in the HUD now
 * ({@link com.butcher.render.CutsceneHud}), so the pause menu, the inventory, a server's chest -
 * all open over a cutscene that simply keeps playing underneath. Unlike SkyMaxx, opening the
 * pause menu does not skip it: the game opens that menu by itself when the window loses focus,
 * and alt-tabbing is not asking to skip.
 *
 * <b>It is {@code Gui.setScreen} on 26.2, not {@code Minecraft.setScreen}</b> - see CLAUDE.md.
 */
@Mixin(Gui.class)
public class GuiScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void butcher$keepChatShut(Screen screen, CallbackInfo ci) {
        if (screen instanceof ChatScreen && ButcherClient.chatShut()) ci.cancel();
    }
}
