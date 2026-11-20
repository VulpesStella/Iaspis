package io.github.vulpesstella.iaspis.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.vulpesstella.iaspis.api.MultipartPart;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;

final class MultipartResolver {
    static MultipartPart resolve(TileEntity tile, MovingObjectPosition hit) {
        if (tile == null) return null;
        Class<?> type = tile.getClass();
        while (type != null && !"codechicken.multipart.TileMultipart".equals(type.getName())) {
            type = type.getSuperclass();
        }
        if (type == null) return null;
        try {
            return Members.INSTANCE == null ? null : Members.INSTANCE.resolve(tile, hit);
        } catch (Exception e) {
            WailaBridge.INSTANCE.report("ForgeMultipart hit resolution", e);
        } catch (LinkageError e) {
            WailaBridge.INSTANCE.report("ForgeMultipart hit resolution", e);
        }
        return null;
    }

    private static final class Members {
        static final Members INSTANCE = load();
        final Class<?> extended;
        final Class<?> tuple;
        final Field data;
        final Method first;
        final Method parts;
        final Method type;
        final Method save;

        Members() throws Exception {
            ClassLoader loader = cpw.mods.fml.common.Loader.instance().getModClassLoader();
            extended = Class.forName("codechicken.lib.raytracer.ExtendedMOP", false, loader);
            tuple = Class.forName("scala.Tuple2", false, loader);
            data = extended.getField("data");
            first = tuple.getMethod("_1");
            parts = Class.forName("codechicken.multipart.TileMultipart", false, loader).getMethod("jPartList");
            Class<?> part = Class.forName("codechicken.multipart.TMultiPart", false, loader);
            type = part.getMethod("getType");
            save = part.getMethod("save", NBTTagCompound.class);
        }

        MultipartPart resolve(TileEntity tile, MovingObjectPosition hit) throws Exception {
            if (!extended.isInstance(hit)) return null;
            Object pair = data.get(hit);
            if (!tuple.isInstance(pair)) return null;
            Object number = first.invoke(pair);
            if (!(number instanceof Integer)) return null;
            int index = ((Integer) number).intValue();
            List<?> list = (List<?>) parts.invoke(tile);
            if (index < 0 || index >= list.size()) return null;
            Object part = list.get(index);
            String id = (String) type.invoke(part);
            if (id == null || id.isEmpty()) return null;
            NBTTagCompound tag = new NBTTagCompound();
            save.invoke(part, tag);
            tag.setString("id", id);
            return new MultipartPart(id, index, part, tag);
        }

        static Members load() {
            try {
                return new Members();
            } catch (Exception e) {
                WailaBridge.INSTANCE.report("Unsupported ForgeMultipart hit layout", e);
            } catch (LinkageError e) {
                WailaBridge.INSTANCE.report("Unsupported ForgeMultipart hit layout", e);
            }
            return null;
        }
    }

    private MultipartResolver() {}
}
