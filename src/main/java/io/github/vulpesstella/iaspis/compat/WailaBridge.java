package io.github.vulpesstella.iaspis.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms.IMCMessage;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.api.MultipartPart;
import mcp.mobius.waila.api.IWailaBlockDecorator;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaEntityProvider;
import mcp.mobius.waila.api.IWailaFMPDecorator;
import mcp.mobius.waila.api.IWailaFMPProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.IWailaSummaryProvider;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

@SuppressWarnings("rawtypes") // The WAILA 1.5.2a registrar uses raw Class parameters.
public final class WailaBridge implements IWailaRegistrar, IWailaConfigHandler {
    public static final WailaBridge INSTANCE = new WailaBridge();

    public enum Section { HEAD, BODY, TAIL, OVERRIDE }

    private final List<Registration<IWailaDataProvider>> blocks = new ArrayList<Registration<IWailaDataProvider>>();
    private final List<Registration<IWailaEntityProvider>> entities = new ArrayList<Registration<IWailaEntityProvider>>();
    private final List<Registration<IWailaBlockDecorator>> decorators = new ArrayList<Registration<IWailaBlockDecorator>>();
    private final List<Registration<IWailaFMPProvider>> multipartProviders = new ArrayList<Registration<IWailaFMPProvider>>();
    private final List<Registration<IWailaFMPDecorator>> multipartDecorators = new ArrayList<Registration<IWailaFMPDecorator>>();
    private final List<Registration<String>> syncedKeys = new ArrayList<Registration<String>>();
    private final Map<String, HashMap<String, String>> modules = new LinkedHashMap<String, HashMap<String, String>>();
    private final Set<String> optionKeys = new LinkedHashSet<String>();
    private final Set<String> remoteOptions = new HashSet<String>();
    private final Set<String> reported = Collections.synchronizedSet(new HashSet<String>());
    private Logger log;

    private WailaBridge() {
    }

    public void initialize(Logger logger) {
        log = logger;
    }

    public void receive(List<IMCMessage> messages) {
        for (IMCMessage message : messages) {
            if (!message.isStringMessage()) continue;
            String value = message.getStringValue();
            try {
                if ("register".equalsIgnoreCase(message.key)) {
                    if ("WailaHarvestability".equals(message.getSender())
                            && "squeek.wailaharvestability.WailaHandler.callbackRegister".equals(value)) continue;
                    int separator = value.lastIndexOf('.');
                    if (separator < 1 || separator == value.length() - 1) {
                        throw new IllegalArgumentException("Expected a fully qualified registration method");
                    }
                    Class<?> owner = Class.forName(value.substring(0, separator), true,
                            Loader.instance().getModClassLoader());
                    Method callback = owner.getDeclaredMethod(value.substring(separator + 1), IWailaRegistrar.class);
                    callback.invoke(null, this);
                    log.info("Registered WAILA providers from " + message.getSender());
                } else if ("addconfig".equalsIgnoreCase(message.key)) {
                    String[] fields = value.split("\\$\\$", 3);
                    if (fields.length != 3) throw new IllegalArgumentException("Expected module$$key$$label");
                    addConfig(fields[0], fields[1], fields[2]);
                }
            } catch (Exception e) {
                report("WAILA IMC from " + message.getSender() + ": " + value, e);
            } catch (LinkageError e) {
                report("WAILA IMC from " + message.getSender() + ": " + value, e);
            }
        }
    }

    public void report(String context, Throwable error) {
        if (reported.add(context)) log.log(Level.WARNING, context, error);
    }

    public void addConfig(String module, String key, String label) {
        HashMap<String, String> keys = modules.get(module);
        if (keys == null) {
            keys = new LinkedHashMap<String, String>();
            modules.put(module, keys);
        }
        keys.put(key, label);
        optionKeys.add(key);
    }

    public void addConfig(String module, String key) { addConfig(module, key, "option." + key); }
    public void addConfigRemote(String module, String key, String label) {
        addConfig(module, key, label);
        remoteOptions.add(key);
    }
    public void addConfigRemote(String module, String key) { addConfigRemote(module, key, "option." + key); }
    public Set<String> getModuleNames() { return new LinkedHashSet<String>(modules.keySet()); }

    public HashMap<String, String> getConfigKeys(String module) {
        Map<String, String> keys = modules.get(module);
        HashMap<String, String> labels = new LinkedHashMap<String, String>();
        if (keys != null) {
            for (Map.Entry<String, String> key : keys.entrySet()) {
                labels.put(key.getKey(), StatCollector.translateToLocal(key.getValue()));
            }
        }
        return labels;
    }

    public boolean getConfig(String key) { return getConfig(key, true); }

    public boolean getConfig(String key, boolean defaultValue) {
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            if (requiresServer(key) && !Iaspis.proxy.hasWailaServer()) return false;
            Boolean forced = forcedOption(key);
            if (forced != null) return forced;
        }
        return Iaspis.config().getBoolean(key, defaultValue);
    }

    public boolean requiresServer(String key) { return remoteOptions.contains(key); }

    public Boolean forcedOption(String key) {
        return optionKeys.contains(key) ? Iaspis.proxy.forcedWailaOption(key) : null;
    }

    Map<String, Boolean> lockedOptions() {
        Map<String, Boolean> options = new LinkedHashMap<String, Boolean>();
        for (String key : optionKeys) {
            if (Iaspis.config().isWailaOptionLocked(key)) options.put(key, Iaspis.config().getBoolean(key));
        }
        return options;
    }

    public List<IWailaDataProvider> blocks(Section section, Target target) {
        return matching(blocks, section, target.getBlockID(), target.getBlock(), target.getTileEntity());
    }

    public List<IWailaEntityProvider> entities(Section section, Target target) {
        return matching(entities, section, -1, target.getEntity(), null);
    }

    public List<IWailaBlockDecorator> decorators(Target target) {
        return matching(decorators, Section.BODY, target.getBlockID(), target.getBlock(), target.getTileEntity());
    }

    public List<String> syncedKeys(int blockId, Object target, Object tile) {
        List<String> keys = matching(syncedKeys, Section.BODY, blockId, target, tile);
        if (hasMultipartProviders() && isMultipart(target) && !keys.contains("*")) keys.add("*");
        return keys;
    }

    public List<MultipartAccessor> multipart(Target target) {
        MultipartPart part = target.getMultipartPart();
        if (!hasMultipartProviders() || part == null) return Collections.emptyList();
        return Collections.singletonList(new MultipartAccessor(target, part.getType(), part.getSnapshot()));
    }

    public List<IWailaFMPProvider> multipartProviders(Section section, String id) {
        return matchingPart(multipartProviders, section, id);
    }

    public List<IWailaFMPDecorator> multipartDecorators(String id) {
        return matchingPart(multipartDecorators, Section.BODY, id);
    }

    private boolean hasMultipartProviders() {
        return !multipartProviders.isEmpty() || !multipartDecorators.isEmpty();
    }

    static boolean isMultipart(Object block) {
        Class<?> type = block == null ? null : block.getClass();
        for (; type != null; type = type.getSuperclass()) {
            if ("codechicken.multipart.BlockMultipart".equals(type.getName())) return true;
        }
        return false;
    }

    private static <T> List<T> matchingPart(List<Registration<T>> registrations, Section section, String id) {
        Set<T> providers = new LinkedHashSet<T>();
        for (Registration<T> registration : registrations) {
            if (registration.section == section && id.equals(registration.matcher)) providers.add(registration.provider);
        }
        return new ArrayList<T>(providers);
    }

    private static <T> List<T> matching(List<Registration<T>> registrations, Section section,
                                         int blockId, Object target, Object tile) {
        Set<T> result = new LinkedHashSet<T>();
        for (Registration<T> registration : registrations) {
            if (registration.section == section && (registration.matcher.equals(blockId)
                    || registration.matches(target))) result.add(registration.provider);
        }
        for (Registration<T> registration : registrations) {
            if (registration.section == section && registration.matches(tile)) result.add(registration.provider);
        }
        return new ArrayList<T>(result);
    }

    private static final class Registration<T> {
        final Section section;
        final Object matcher;
        final T provider;

        Registration(Section section, Object matcher, T provider) {
            this.section = section;
            this.matcher = matcher;
            this.provider = provider;
        }

        boolean matches(Object target) {
            return matcher instanceof Class && ((Class<?>) matcher).isInstance(target);
        }
    }

    public void registerHeadProvider(IWailaDataProvider provider, int blockId) {
        blocks.add(new Registration<IWailaDataProvider>(Section.HEAD, blockId, provider));
    }

    public void registerHeadProvider(IWailaDataProvider provider, Class type) {
        blocks.add(new Registration<IWailaDataProvider>(Section.HEAD, type, provider));
    }

    public void registerHeadProvider(IWailaEntityProvider provider, Class type) {
        entities.add(new Registration<IWailaEntityProvider>(Section.HEAD, type, provider));
    }

    public void registerBodyProvider(IWailaDataProvider provider, int blockId) {
        blocks.add(new Registration<IWailaDataProvider>(Section.BODY, blockId, provider));
    }

    public void registerBodyProvider(IWailaDataProvider provider, Class type) {
        blocks.add(new Registration<IWailaDataProvider>(Section.BODY, type, provider));
    }

    public void registerBodyProvider(IWailaEntityProvider provider, Class type) {
        entities.add(new Registration<IWailaEntityProvider>(Section.BODY, type, provider));
    }

    public void registerTailProvider(IWailaDataProvider provider, int blockId) {
        blocks.add(new Registration<IWailaDataProvider>(Section.TAIL, blockId, provider));
    }

    public void registerTailProvider(IWailaDataProvider provider, Class type) {
        blocks.add(new Registration<IWailaDataProvider>(Section.TAIL, type, provider));
    }

    public void registerTailProvider(IWailaEntityProvider provider, Class type) {
        entities.add(new Registration<IWailaEntityProvider>(Section.TAIL, type, provider));
    }

    public void registerStackProvider(IWailaDataProvider provider, int blockId) {
        blocks.add(new Registration<IWailaDataProvider>(Section.OVERRIDE, blockId, provider));
    }

    public void registerStackProvider(IWailaDataProvider provider, Class type) {
        blocks.add(new Registration<IWailaDataProvider>(Section.OVERRIDE, type, provider));
    }

    public void registerOverrideEntityProvider(IWailaEntityProvider provider, Class type) {
        entities.add(new Registration<IWailaEntityProvider>(Section.OVERRIDE, type, provider));
    }

    public void registerDecorator(IWailaBlockDecorator provider, int blockId) {
        decorators.add(new Registration<IWailaBlockDecorator>(Section.BODY, blockId, provider));
    }

    public void registerDecorator(IWailaBlockDecorator provider, Class type) {
        decorators.add(new Registration<IWailaBlockDecorator>(Section.BODY, type, provider));
    }

    public void registerSyncedNBTKey(String key, Class target) {
        syncedKeys.add(new Registration<String>(Section.BODY, target, key));
    }

    public void registerHeadProvider(IWailaFMPProvider provider, String part) {
        multipartProviders.add(new Registration<IWailaFMPProvider>(Section.HEAD, part, provider));
    }

    public void registerBodyProvider(IWailaFMPProvider provider, String part) {
        multipartProviders.add(new Registration<IWailaFMPProvider>(Section.BODY, part, provider));
    }

    public void registerTailProvider(IWailaFMPProvider provider, String part) {
        multipartProviders.add(new Registration<IWailaFMPProvider>(Section.TAIL, part, provider));
    }

    public void registerDecorator(IWailaFMPDecorator provider, String part) {
        multipartDecorators.add(new Registration<IWailaFMPDecorator>(Section.BODY, part, provider));
    }

    public void registerDocTextFile(String filename) {}
    public void registerShortDataProvider(IWailaSummaryProvider provider, Class item) {}
}
