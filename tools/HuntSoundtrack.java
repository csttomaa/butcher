import com.butcher.anim.ChopTimeline;
import com.butcher.audio.HuntSound;
import com.butcher.audio.SynthEngine;
import com.butcher.audio.Voice;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Renders the Mythological Dye's ending ("The Whole Hunt") after the split for a drop to a WAV - the exact cue list the game plays
 * (HuntSound.schedule), mixed offline. A cut silences everything already sounding, as
 * ButcherAudio.silence() does in game. The price's coin ticks are not in it.
 *
 *   java -cp "out;src/main/resources" HuntSoundtrack <outdir> <seed>
 */
public final class HuntSoundtrack {

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        long seed = Long.parseLong(args[1]);
        int rate = (int) SynthEngine.SAMPLE_RATE;
        {
            String d = "dye";
            ChopTimeline tl = new ChopTimeline(ChopTimeline.Verdict.DYE, ChopTimeline.Creature.SPHINX, seed);
            float seconds = tl.total() / 1000f + 2.5f;
            float[] mix = render(HuntSound.schedule(tl), seconds);
            float peak = 0f;
            for (float v : mix) peak = Math.max(peak, Math.abs(v));
            float gain = peak > 0.98f ? 0.98f / peak : 1f;
            byte[] pcm = new byte[mix.length * 2];
            for (int i = 0; i < mix.length; i++) {
                int s = Math.round(Math.max(-1f, Math.min(1f, mix[i] * gain)) * 32767f);
                pcm[i * 2] = (byte) s;
                pcm[i * 2 + 1] = (byte) (s >> 8);
            }
            AudioFormat fmt = new AudioFormat(rate, 16, 2, true, false);
            File f = new File(dir, "hunt_" + d + ".wav");
            AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), fmt, mix.length / 2), AudioFileFormat.Type.WAVE, f);
            System.out.printf("%s: %.1f s, peak %.3f%s%n", d, seconds, peak, gain < 1f ? " (normalised)" : "");
        }
    }

    /** The cues mixed into one interleaved stereo buffer. */
    public static float[] render(List<HuntSound.Cue> cues, float seconds) {
        int rate = (int) SynthEngine.SAMPLE_RATE;
        float[] mix = new float[(int) (seconds * rate) * 2];
        for (HuntSound.Cue c : cues) {
            int at = (int) (c.at * rate / 1000L) * 2;
            if (at >= mix.length) continue;
            if (c.cut) Arrays.fill(mix, at, mix.length, 0f);
            float left = seconds - c.at / 1000f;
            for (Voice v : c.voices) {
                float[] one = SynthEngine.renderOffline(v, left);
                for (int i = 0; i < one.length && at + i < mix.length; i++) mix[at + i] += one[i];
            }
        }
        return mix;
    }
}
