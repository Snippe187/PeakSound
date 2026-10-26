package de.peaksound.plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Comparator;

/**
 * Setzt den Ausgang eines Comparators direkt (0-15). Die Bukkit-API kennt das nicht: Ein Comparator liest sonst
 * nur Behaelter, und ein Notenblock hat keinen Inhalt. Deshalb greift diese Klasse per Reflection auf die
 * Server-Interna zu (Mojang-Namen, seit Minecraft 26 ohne Remapping stabil). Ist etwas nicht auffindbar,
 * liefert {@link #create} nichts, und das Plugin schaltet nur den Equalizer ab; der Rest laeuft normal weiter.
 *
 * <p>Vanilla wuerde den Ausgang bei jeder Nachbaraenderung neu berechnen und auf 0 zuruecksetzen. Das
 * verhindert der EqualizerService, indem er die Physik-Ereignisse dieser Comparators abbricht.
 */
final class ComparatorDriver {
    private final Method getHandle;
    private final Constructor<?> blockPosCtor;
    private final Method getBlockEntity;
    private final Method getBlockState;
    private final Method stateGetBlock;
    private final Method setOutputSignal;
    private final Method getOutputSignal;
    private final Method updateNeighborsInFront;
    private final Class<?> comparatorEntityClass;

    private ComparatorDriver(Method getHandle, Constructor<?> blockPosCtor, Method getBlockEntity, Method getBlockState,
                             Method stateGetBlock, Method setOutputSignal, Method getOutputSignal,
                             Method updateNeighborsInFront, Class<?> comparatorEntityClass) {
        this.getHandle = getHandle;
        this.blockPosCtor = blockPosCtor;
        this.getBlockEntity = getBlockEntity;
        this.getBlockState = getBlockState;
        this.stateGetBlock = stateGetBlock;
        this.setOutputSignal = setOutputSignal;
        this.getOutputSignal = getOutputSignal;
        this.updateNeighborsInFront = updateNeighborsInFront;
        this.comparatorEntityClass = comparatorEntityClass;
    }

    static Optional<ComparatorDriver> create(Logger log) {
        try {
            Class<?> craftWorld = Class.forName("org.bukkit.craftbukkit.CraftWorld");
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos");
            Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState");
            Class<?> entity = Class.forName("net.minecraft.world.level.block.entity.ComparatorBlockEntity");
            Class<?> diode = Class.forName("net.minecraft.world.level.block.DiodeBlock");

            Method updateInFront = null;
            for (Method m : diode.getDeclaredMethods()) {
                if (m.getName().equals("updateNeighborsInFront") && m.getParameterCount() == 3
                        && !Modifier.isStatic(m.getModifiers())) {
                    updateInFront = m;
                    break;
                }
            }
            if (updateInFront == null) throw new NoSuchMethodException("DiodeBlock.updateNeighborsInFront");
            updateInFront.setAccessible(true);

            Method getHandle = craftWorld.getMethod("getHandle");
            Constructor<?> posCtor = blockPos.getConstructor(int.class, int.class, int.class);
            Method getBlockEntity = level.getMethod("getBlockEntity", blockPos);
            Method getBlockState = level.getMethod("getBlockState", blockPos);
            Method stateGetBlock = blockState.getMethod("getBlock");
            Method setOutput = entity.getMethod("setOutputSignal", int.class);
            Method getOutput = entity.getMethod("getOutputSignal");
            return Optional.of(new ComparatorDriver(getHandle, posCtor, getBlockEntity, getBlockState, stateGetBlock,
                    setOutput, getOutput, updateInFront, entity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.log(Level.WARNING, "Comparator-Equalizer ist aus: Server-Interna nicht gefunden (" + e + ")");
            return Optional.empty();
        }
    }

    /**
     * Setzt den Comparator-Ausgang. Nur auf dem Haupt-Thread aufrufen.
     *
     * @return false, wenn der Block kein Comparator ist oder etwas schiefging
     */
    boolean set(Block block, int level) {
        if (block.getType() != Material.COMPARATOR) return false;
        int clamped = Math.max(0, Math.min(15, level));
        try {
            Object nmsLevel = getHandle.invoke(block.getWorld());
            Object pos = blockPosCtor.newInstance(block.getX(), block.getY(), block.getZ());
            Object be = getBlockEntity.invoke(nmsLevel, pos);
            if (!comparatorEntityClass.isInstance(be)) return false;
            int before = (int) getOutputSignal.invoke(be);
            Comparator data = (Comparator) block.getBlockData();
            boolean on = clamped > 0;
            boolean stateChanged = data.isPowered() != on;
            if (before == clamped && !stateChanged) return true;
            setOutputSignal.invoke(be, clamped);
            if (stateChanged) {
                data.setPowered(on);
                block.setBlockData(data, false); // ohne Physik: den Nachbarn melden wir selbst
            }
            // Dem Block vor dem Comparator (Staub, Repeater, Lampe ...) melden, dass sich das Signal geaendert hat
            Object state = getBlockState.invoke(nmsLevel, pos);
            Object nmsBlock = stateGetBlock.invoke(state);
            updateNeighborsInFront.invoke(nmsBlock, nmsLevel, pos, state);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException | RuntimeException e) {
            return false;
        }
    }

    /** Aktueller Ausgang (nur fuer Diagnose/Tests), oder -1. */
    int read(Block block) {
        try {
            Object nmsLevel = getHandle.invoke(block.getWorld());
            Object be = getBlockEntity.invoke(nmsLevel, blockPosCtor.newInstance(block.getX(), block.getY(), block.getZ()));
            return comparatorEntityClass.isInstance(be) ? (int) getOutputSignal.invoke(be) : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
