package littlh.broken_chronicles.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {
    public static final KeyMapping READ = new KeyMapping(
            "key.broken_chronicles.read",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.broken_chronicles");

    private ModKeyMappings() {
    }
}
