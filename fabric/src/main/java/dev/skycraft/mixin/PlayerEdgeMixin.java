package dev.skycraft.mixin;

import dev.skycraft.link.SkyLink;
import dev.skycraft.world.SkyCollision;
import dev.skycraft.world.SkyEdge;
import dev.skycraft.world.SkyTri;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Crouching stops at edges in the mirror: Minecraft blocks' and Skyrim's (SkyEdge). Minecraft looks
 * for block collision under the player to decide where an edge is; the Skyrim ground the player walks
 * on isn't blocks (the player collides with its exact triangles), so on its own every direction looked
 * like a drop and crouching froze the player. Here Skyrim's surfaces count as ground too.
 */
@Mixin(Player.class)
public abstract class PlayerEdgeMixin {
	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void skycraft$stopAtSkyrimEdges(Vec3 delta, MoverType moverType, CallbackInfoReturnable<Vec3> cir) {
		Player self = (Player) (Object) this;
		if (!SkyLink.active() || !dev.skycraft.SkyDimension.inMirror(self.level())) {
			return;
		}
		if (self instanceof ServerPlayer) {
			// The player's own client has stopped the move at the edges already, against its Skyrim's
			// ground, which a server doesn't collide players with: the move stands.
			cir.setReturnValue(delta);
			return;
		}
		if (!SkyCollision.usesSmoothCollider(self)) {
			return; // Skyrim's ground is blocks' shapes for this player: vanilla sees it
		}
		double step = self.maxUpStep();
		if (self.getAbilities().flying || delta.y > 0.0 || (moverType != MoverType.SELF && moverType != MoverType.PLAYER) || !self.isShiftKeyDown()) {
			cir.setReturnValue(delta);
			return;
		}
		AABB box = self.getBoundingBox();
		List<SkyTri> tris = new ArrayList<>();
		SkyCollision.trianglesNear(new AABB(box.minX + Math.min(delta.x, 0.0) - 0.1, box.minY - step - 0.1, box.minZ + Math.min(delta.z, 0.0) - 0.1,
			box.maxX + Math.max(delta.x, 0.0) + 0.1, box.minY + 0.1, box.maxZ + Math.max(delta.z, 0.0) + 0.1), tris);
		SkyEdge.Drop drop = (dx, dz) -> skycraft$drops(self, box, tris, dx, dz, step);
		boolean aboveGround = self.onGround() || self.fallDistance < step && !skycraft$drops(self, box, tris, 0.0, 0.0, step - self.fallDistance);
		if (!aboveGround) {
			cir.setReturnValue(delta);
			return;
		}
		double[] move = SkyEdge.backOff(delta.x, delta.z, drop);
		cir.setReturnValue(move[0] == delta.x && move[1] == delta.z ? delta : new Vec3(move[0], delta.y, move[1]));
	}

	/** Vanilla's canFallAtLeast (no block within {@code depth} under the moved box), and no Skyrim surface there either. */
	private static boolean skycraft$drops(Player player, AABB box, List<SkyTri> tris, double dx, double dz, double depth) {
		AABB below = new AABB(box.minX + 1.0E-7 + dx, box.minY - depth - 1.0E-7, box.minZ + 1.0E-7 + dz, box.maxX - 1.0E-7 + dx, box.minY,
			box.maxZ - 1.0E-7 + dz);
		// (up to a hair over the feet: the player stands on a surface at its exact height)
		return player.level().noCollision(player, below)
			&& !SkyEdge.surfaceUnder(tris, below.minX, below.minZ, below.maxX, below.maxZ, below.minY, below.maxY + 0.01);
	}
}
