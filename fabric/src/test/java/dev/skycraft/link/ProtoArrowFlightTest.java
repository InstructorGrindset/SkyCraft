package dev.skycraft.link;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtoArrowFlightTest {
	/** What Skyrim sends (skse/src/Arrows.cpp, Arrows::PerFrame) for a flight direction. */
	private static int code(double x, double y, double z) {
		double length = Math.sqrt(x * x + y * y + z * z);
		x /= length;
		y /= length;
		z /= length;
		int yaw = (int) Math.round(Math.atan2(x, z) / (Math.PI * 2.0) * 256.0) & 0xFF;
		int pitch = (int) Math.round((Math.asin(y) / Math.PI + 0.5) * 255.0);
		return yaw << 8 | pitch;
	}

	private static void roundTrip(double x, double y, double z) {
		double length = Math.sqrt(x * x + y * y + z * z);
		double[] d = Proto.arrowFlight(code(x, y, z));
		double dot = (d[0] * x + d[1] * y + d[2] * z) / length;
		double degrees = Math.toDegrees(Math.acos(Math.min(1.0, dot)));
		assertTrue(degrees < 1.5, "(" + x + ", " + y + ", " + z + ") came back " + degrees + " degrees off");
		assertTrue(Math.abs(d[0] * d[0] + d[1] * d[1] + d[2] * d[2] - 1.0) < 1e-9, "unit length");
	}

	@Test
	void flightDirectionsComeBackWithinADegreeAndAHalf() {
		roundTrip(0, 0, 1);
		roundTrip(1, 0, 0);
		roundTrip(-1, 0, 0);
		roundTrip(0, 0, -1);
		roundTrip(3, -0.4, -2);
		roundTrip(-0.2, 0.3, 5);
		roundTrip(0.01, -1, 0.02);
		roundTrip(1, 1, 1);
	}
}
