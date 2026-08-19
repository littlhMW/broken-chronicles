package littlh.broken_chronicles.content;

import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * book 条目与原版成书的匹配规则：id / title / author 全部可选，写了才参与匹配。
 */
public record BookMatch(String itemId, String title, String author) {

    public static BookMatch parse(JsonObject object) {
        String itemId = object.has("id") ? object.get("id").getAsString() : null;
        String title = object.has("title") ? object.get("title").getAsString() : null;
        String author = object.has("author") ? object.get("author").getAsString() : null;
        if (itemId == null && title == null && author == null) return null;
        return new BookMatch(itemId, title, author);
    }

    public boolean matches(ItemStack stack) {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) return false;
        if (itemId != null) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
            if (stack.getItem() != BuiltInRegistries.ITEM.get(id)) return false;
        }
        if (title != null && !title.equals(content.title().raw())) return false;
        if (author != null && !author.equals(content.author())) return false;
        return true;
    }
}
