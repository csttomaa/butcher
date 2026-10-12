package com.butcher.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.equine.AbstractEquineModel;
import net.minecraft.client.model.animal.equine.EquineSaddleModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.squid.SquidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.guardian.GuardianModel;
import net.minecraft.client.model.monster.slime.MagmaCubeModel;
import net.minecraft.client.model.object.skull.SkullModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.entity.state.SquidRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The lava creatures as the game builds them: the real vanilla models - the iron golem (Lord Jawbus), the elder
 * guardian (Thunder), the squid (Plhlegblast), a player in Ragnarok's skin riding a skeleton horse with its
 * saddle, the player-head skull (the Fiery Scuttler, its legs in the hat layer) and the magma cube (Kuudra and
 * its tentacles) - each built from its own layer, posed by its own {@code setupAnim} where that needs no game
 * world, and walked with {@link ModelPart#visit} for every posed polygon and its UVs, the way DragonRig does
 * the Ender Dragon. The same class runs in the game and in the offline preview.
 *
 * Output, per polygon: four corners of {x, y, z, u, v} in blocks (y up, +Z towards the camera at yaw 0, the
 * model's own front), then [20] the texture slot ({@link EruptionScene}'s TEX_* by slot) and [21] flags.
 */
public final class LavaRigs implements EruptionScene.Rigs {

    private final IronGolemModel golem;
    private final IronGolemRenderState golemState = new IronGolemRenderState();
    private final GuardianModel guardian;
    private final GuardianRenderState guardianState = new GuardianRenderState();
    private final SquidModel squid;
    private final SquidRenderState squidState = new SquidRenderState();
    private final PlayerModel rider;
    private final ModelPart riderRoot;
    private final ModelPart horseRoot, saddleRoot;
    private final ModelPart skullRoot;
    private final MagmaCubeModel cube;
    private final SlimeRenderState cubeState = new SlimeRenderState();

    public LavaRigs() {
        golem = new IronGolemModel(IronGolemModel.createBodyLayer().bakeRoot());
        guardian = new GuardianModel(GuardianModel.createElderGuardianLayer().bakeRoot());
        squid = new SquidModel(SquidModel.createBodyLayer().bakeRoot());
        riderRoot = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        rider = new PlayerModel(riderRoot, false);
        horseRoot = LayerDefinition.create(AbstractEquineModel.createBodyMesh(CubeDeformation.NONE), 64, 64).bakeRoot();
        saddleRoot = EquineSaddleModel.createSaddleLayer().bakeRoot();
        skullRoot = SkullModel.createHumanoidHeadLayer().bakeRoot();
        cube = new MagmaCubeModel(MagmaCubeModel.createBodyLayer().bakeRoot());
    }

    // ------------------------------------------------------------------ the creatures

    @Override
    public List<float[]> golem(float walk, float yaw, float armRaise) {
        golemState.walkAnimationPos = walk;
        golemState.walkAnimationSpeed = walk == 0f ? 0f : 0.6f;
        golemState.attackTicksRemaining = 0f;
        golemState.offerFlowerTick = 0;
        golemState.yRot = 0f;
        golemState.xRot = 0f;
        golemState.ageInTicks = walk * 4f;
        golem.setupAnim(golemState);
        if (armRaise != 0f) {
            golem.root().getChild("right_arm").xRot -= armRaise;
            golem.root().getChild("left_arm").xRot -= armRaise;
            // Past the horizontal the arms also spread and the head goes back: agony, not a doorframe (the review,
            // E19 - straight out front, two arms read as a frame round him).
            float over = Math.max(0f, armRaise - 1f);
            golem.root().getChild("right_arm").zRot += 0.45f * over;
            golem.root().getChild("left_arm").zRot -= 0.45f * over;
            golem.root().getChild("head").xRot -= 0.35f * over;
        }
        List<float[]> out = new ArrayList<>();
        emit(golem.root(), living(yaw), 0, out);
        return out;
    }

    @Override
    public List<float[]> guardian(float spikes, float tail, float yaw, float lookX, float lookY) {
        guardianState.spikesAnimation = spikes;
        guardianState.tailAnimation = tail;
        guardianState.ageInTicks = tail * 20f;
        guardianState.yRot = 0f;
        guardianState.xRot = 0f;
        guardianState.eyePosition = Vec3.ZERO;
        guardianState.lookDirection = new Vec3(0, 0, 1);
        guardianState.lookAtPosition = new Vec3(lookX * 8, lookY * 8, 16);
        guardianState.attackTargetPosition = null;
        guardian.setupAnim(guardianState);
        List<float[]> out = new ArrayList<>();
        emit(guardian.root(), living(yaw), 0, out);
        return out;
    }

    @Override
    public List<float[]> squid(float tentacle, float xBody, float yaw) {
        squidState.tentacleAngle = tentacle;
        squidState.xBodyRot = xBody;
        squidState.zBodyRot = 0f;
        squidState.ageInTicks = 0f;
        squid.setupAnim(squidState);
        // SquidRenderer.setupRotations: up half a block, turned, tilted, down 1.2.
        PoseStack ps = new PoseStack();
        ps.translate(0f, 0.5f, 0f);
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.mulPose(Axis.XP.rotationDegrees(xBody));
        ps.translate(0f, -1.2f, 0f);
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        List<float[]> out = new ArrayList<>();
        emit(squid.root(), ps, 0, out);
        return out;
    }

    /**
     * Ragnarok: a player in its skin (slot 0) riding a skeleton horse (slot 1) with its saddle (slot 2). The
     * rider's pose is HumanoidModel's riding pose, set here by hand (its own setupAnim reads held items, which
     * need a running game); the horse is AbstractEquineModel's own standing rest, legs and neck as built.
     */
    @Override
    public List<float[]> rider(float yaw, float rear, float armRaise) {
        resetParts(riderRoot);
        ModelPart rl = riderRoot.getChild("right_leg"), ll = riderRoot.getChild("left_leg");
        ModelPart ra = riderRoot.getChild("right_arm"), la = riderRoot.getChild("left_arm");
        // HumanoidModel.setupAnim with isPassenger: arms -36 degrees, legs swung forward and splayed.
        ra.xRot = -0.62831855f - armRaise;
        la.xRot = -0.62831855f - armRaise * 0.6f;
        rl.xRot = -1.4137167f;
        rl.yRot = 0.31415927f;
        rl.zRot = 0.07853982f;
        ll.xRot = -1.4137167f;
        ll.yRot = -0.31415927f;
        ll.zRot = -0.07853982f;
        resetParts(horseRoot);
        resetParts(saddleRoot);
        // A rearing horse: the body tips back about its hind legs.
        float tip = rear * 0.75f;
        List<float[]> out = new ArrayList<>();
        PoseStack horse = living(yaw);
        horse.mulPose(Axis.XP.rotation(-tip));
        emit(horseRoot, horse, 1, out);
        PoseStack saddle = living(yaw);
        saddle.mulPose(Axis.XP.rotation(-tip));
        emit(saddleRoot, saddle, 2, out);
        // The rider sits on the saddle: up and back a little from the horse's origin, turning with it.
        PoseStack man = living(yaw);
        man.mulPose(Axis.XP.rotation(-tip));
        man.translate(0f, -0.72f, 0.2f);
        emit(riderRoot, man, 0, out);
        return out;
    }

    @Override
    public List<float[]> skull(float yaw, float pitch) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.scale(-1f, -1f, 1f);
        // Centred on the head's middle: the skull's model has its origin at the neck (bottom).
        ps.translate(0f, 0.25f, 0f);
        List<float[]> out = new ArrayList<>();
        emit(skullRoot, ps, 0, out);
        return out;
    }

    @Override
    public List<float[]> magmaCube(float squish, float yaw) {
        cubeState.squish = squish;
        cubeState.size = 1;
        cubeState.ageInTicks = 0f;
        cube.setupAnim(cubeState);
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        // AbstractCubeMobRenderer's squish: wider as it lands, taller as it jumps.
        float s = 1f / (squish / 2f + 1f);
        ps.scale(s, 1f / s, s);
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        List<float[]> out = new ArrayList<>();
        emit(cube.root(), ps, 0, out);
        return out;
    }

    // ------------------------------------------------------------------ helpers

    /** LivingEntityRenderer's transform for a body turned to {@code yaw}: yaw 0 faces the camera (+Z). */
    private static PoseStack living(float yaw) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        return ps;
    }

    private static void resetParts(ModelPart root) {
        root.getAllParts().forEach(ModelPart::resetPose);
    }

    /** Every polygon of {@code root} under {@code ps}, into {@code out} with texture {@code slot}. */
    private static void emit(ModelPart root, PoseStack ps, int slot, List<float[]> out) {
        Vector3f v = new Vector3f();
        root.visit(ps, (pose, path, index, cube) -> {
            Matrix4f m = pose.pose();
            for (ModelPart.Polygon poly : cube.polygons) {
                ModelPart.Vertex[] vs = poly.vertices();
                float[] q = new float[25];
                for (int k = 0; k < 4; k++) {
                    m.transformPosition(vs[k].worldX(), vs[k].worldY(), vs[k].worldZ(), v);
                    q[k * 5] = v.x;
                    q[k * 5 + 1] = v.y;
                    q[k * 5 + 2] = v.z;
                    q[k * 5 + 3] = vs[k].u();
                    q[k * 5 + 4] = vs[k].v();
                }
                q[20] = slot;
                // The guardian's eye, the head of anything: flagged for the scene (the eye glows, heads take light).
                q[21] = path.contains("eye") ? 1f : (path.contains("head") ? 2f : (path.contains("inside") ? 3f : 0f));
                // The polygon's own outward normal, posed as the game poses it: faces turned away are culled.
                pose.transformNormal(poly.normal(), v);
                q[22] = v.x;
                q[23] = v.y;
                q[24] = v.z;
                out.add(q);
            }
        });
    }
}
