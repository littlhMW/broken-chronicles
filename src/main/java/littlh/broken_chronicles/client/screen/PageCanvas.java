package littlh.broken_chronicles.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import littlh.broken_chronicles.ModConfig;
import littlh.broken_chronicles.ModMindEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 纸张画布的几何：阅读界面和书写界面共用同一套排版，两边看到的位置、字号、换行完全一致。
 * <ul>
 *     <li>背景材质：整张画布等比缩放居中（无页边距），画布多大就画多大，透明处透出深色底。</li>
 *     <li>正文框：尺寸锁死（宽 = 画布宽 32%，高 = 画布高 66%），位置跟着纸张的非透明区域走。</li>
 * </ul>
 * 改这里就等于同时改阅读与书写，别在两边各写一份。
 */
@OnlyIn(Dist.CLIENT)
public final class PageCanvas {
    /** 材质画布统一横屏尺寸（16:9）。非透明区域多大就显示多大。 */
    public static final int CANVAS_WIDTH = 512;
    public static final int CANVAS_HEIGHT = 288;
    /** 页边距：0 = 材质按 100% 屏幕显示。 */
    public static final int SCREEN_MARGIN = 0;
    /** 有作者署名时，标题下面那行占的高度。 */
    public static final int NARRATOR_LINE_HEIGHT = 11;

    /** 材质在屏幕上的绘制位置、画布尺寸与非透明区域（content = 非透明区域映射到屏幕后的位置）。 */
    public record Layout(int x, int y, int w, int h, int u, int v, int uw, int vh, int texW, int texH,
                         int contentX, int contentY, int contentW, int contentH) {
    }

    /** 正文区域（纸张中间那块 3:4 的单页）。 */
    public record TextArea(int x, int y, int width, int height) {
    }

    /** 材质路径 -> {texW, texH, minX, minY, maxX, maxY} 非透明区域。 */
    private static final Map<ResourceLocation, int[]> TEXTURE_BOUNDS = new HashMap<>();

    private PageCanvas() {
    }

    /** 资源包重载后清空材质边界缓存（F3+T、切换资源包后重新计算）。 */
    public static void invalidate() {
        TEXTURE_BOUNDS.clear();
    }

    /** 材质文件是否存在（资源包里找不到时返回 false，避免渲染成紫黑方块）。 */
    public static boolean exists(ResourceLocation location) {
        if (location == null) return false;
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }

    /** 兜底材质：配置里第一个存在的默认材质，都没有就用内置 oldpaper。 */
    public static ResourceLocation defaultTexture() {
        for (String def : ModConfig.DEFAULT_PAGE_TEXTURES.get()) {
            try {
                ResourceLocation t = ResourceLocation.parse(def);
                if (exists(t)) return t;
            } catch (Exception ignored) {
            }
        }
        return ResourceLocation.fromNamespaceAndPath(ModMindEntry.MOD_ID, "textures/gui/page/oldpaper.png");
    }

    /** 背景按整张画布等比缩放居中（无页边距）：画布多大就显示多大，所见即所得。 */
    public static Layout layout(int screenWidth, int screenHeight, ResourceLocation texture) {
        int[] b = bounds(texture);
        int texW = Math.max(1, b[0]);
        int texH = Math.max(1, b[1]);
        int minX = b[2];
        int minY = b[3];
        int maxX = Math.max(minX + 1, b[4]);
        int maxY = Math.max(minY + 1, b[5]);
        int availW = screenWidth - SCREEN_MARGIN * 2;
        int availH = screenHeight - SCREEN_MARGIN * 2;
        double scale = Math.min((double) availW / texW, (double) availH / texH);
        int w = Math.max(1, (int) Math.round(texW * scale));
        int h = Math.max(1, (int) Math.round(texH * scale));
        int x = (screenWidth - w) / 2;
        int y = (screenHeight - h) / 2;
        int contentX = x + (int) Math.round(minX * scale);
        int contentY = y + (int) Math.round(minY * scale);
        int contentW = Math.max(1, (int) Math.round((maxX - minX) * scale));
        int contentH = Math.max(1, (int) Math.round((maxY - minY) * scale));
        return new Layout(x, y, w, h, 0, 0, texW, texH, texW, texH, contentX, contentY, contentW, contentH);
    }

    /**
     * 正文区域：<b>尺寸锁死</b>，只由画布决定（= 格式范例 reading_template.png 里画的那个红框），
     * 和背景材质里纸张画多大、画在哪完全无关：宽 = 画布宽的 32%（约 1/3），高 = 画布高的 66%。
     * <p>
     * 位置才跟着纸张走：水平在纸张中间（和标题对齐），纵向从标题（纸张高度 10% 处）下方 12px 起排。
     *
     * @param hasNarrator 标题下面还有没有作者那一行（有就把正文再往下让 11px）
     */
    public static TextArea textArea(Layout layout, boolean hasNarrator) {
        int areaWidth = Math.max(40, (int) Math.round(layout.w() * 0.32));
        int areaHeight = Math.max(20, (int) Math.round(layout.h() * 0.66));
        int textX = layout.contentX() + (layout.contentW() - areaWidth) / 2;
        int textY = layout.contentY() + (int) (layout.contentH() * 0.10) + 12
                + (hasNarrator ? NARRATOR_LINE_HEIGHT : 0);
        return new TextArea(textX, textY, areaWidth, areaHeight);
    }

    /** 标题那一行的 y（纸张高度 10% 处，居中画）。 */
    public static int titleY(Layout layout) {
        return layout.contentY() + (int) (layout.contentH() * 0.10);
    }

    /** 读取材质像素，计算非透明区域边界（texW, texH, minX, minY, maxX, maxY），带缓存；失败回退整个画布。 */
    public static int[] bounds(ResourceLocation location) {
        int[] cached = TEXTURE_BOUNDS.get(location);
        if (cached != null) return cached;
        int[] fallback = new int[]{CANVAS_WIDTH, CANVAS_HEIGHT, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT};
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) return fallback;
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int w = image.getWidth();
                int h = image.getHeight();
                if (w <= 0 || h <= 0) return fallback;
                int minX = w, minY = h, maxX = -1, maxY = -1;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (((image.getPixelRGBA(x, y) >>> 24) & 0xFF) > 0) {
                            if (x < minX) minX = x;
                            if (x > maxX) maxX = x;
                            if (y < minY) minY = y;
                            if (y > maxY) maxY = y;
                        }
                    }
                }
                if (maxX < 0) return fallback;
                int[] bounds = new int[]{w, h, minX, minY, maxX + 1, maxY + 1};
                TEXTURE_BOUNDS.put(location, bounds);
                return bounds;
            }
        } catch (Exception ignored) {
            return fallback;
        }
    }
}