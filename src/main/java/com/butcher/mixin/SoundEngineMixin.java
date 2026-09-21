package com.butcher.mixin;

import com.butcher.ButcherClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Silences the game while a cutscene owns the screen.
 *
 * <h2>What this is protecting</h2>
 *
 * The cutscene is a reveal, and a reveal can be given away by sound. Hypixel plays its
 * own noises around a kill - the drop chime in particular - and one of those landing
 * while the cleaver is still working up the body tells the player what is coming before
 * the animation gets there. The whole point of chopping a variable number of segments is
 * that nothing on screen counts them; a leaked sound cue undoes that.
 *
 * It also stops the mundane problem of the mining sounds carrying on underneath a
 * full-screen cinematic, which reads as the game still running behind a picture.
 *
 * <h2>Why at the engine and not the volume</h2>
 *
 * Turning the master volume down would be reversible state: if the cutscene is
 * interrupted - a crash, a disconnect, another mod pulling the screen - the player is
 * left muted with no idea why. Refusing each sound as it is submitted holds no state at
 * all. The instant {@link ButcherClient#gameShouldBeSilent()} goes false, sound is
 * back, and if this mixin never ran the game is simply unmodified.
 *
 * Refused, not queued. Deferring sounds and replaying them afterwards is SkyMaxx's
 * {@code s-hold}, and it is a nicer behaviour, but it needs a queue with its own
 * lifetime and a rule for which sources are exempt. Dropping is the honest version of
 * the smaller feature: a handful of mining noises are lost across a few seconds.
 *
 * <h2>Two entry points, not one</h2>
 *
 * {@code playDelayed} schedules a sound for N ticks' time and does not go through
 * {@code play} at submission. Cancelling only {@code play} leaves delayed sounds to
 * arrive mid-cutscene - which is precisely the case that matters, because a drop chime
 * queued on the kill tick is a delayed sound.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    @Inject(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)"
                    + "Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"),
            cancellable = true)
    private void butcher$refuseSound(SoundInstance instance, CallbackInfoReturnable<PlayResult> cir) {
        // No exemptions. The pet sting used to be vanilla's advancement sound and had
        // to be let through here; it is synthesised on our own audio line now, which
        // never reaches this method at all.
        if (ButcherClient.gameShouldBeSilent()) {
            // NOT_STARTED is the engine's own "this did not play" answer, so every caller
            // already handles it. Returning STARTED would leave callers waiting on a
            // channel that was never opened.
            cir.setReturnValue(PlayResult.NOT_STARTED);
        }
    }

    @Inject(
            method = "playDelayed(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void butcher$refuseDelayedSound(SoundInstance instance, int delay, CallbackInfo ci) {
        if (ButcherClient.gameShouldBeSilent()) {
            ci.cancel();
        }
    }
}
