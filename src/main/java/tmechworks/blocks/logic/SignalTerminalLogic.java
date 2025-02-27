package tmechworks.blocks.logic;

import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Facing;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import mantle.world.CoordTuple;
import tmechworks.TMechworks;
import tmechworks.blocks.SignalTerminal;
import tmechworks.common.MechContent;
import tmechworks.lib.signal.ISignalTransceiver;

public class SignalTerminalLogic extends TileEntity implements ISignalTransceiver {

    private byte[] connectedSides = new byte[6];
    private byte[] receivingSides = new byte[6];
    private byte[] sideChannel = new byte[6];

    private boolean forceUpdateSide;
    private boolean neighborChanged;

    private byte[] cachedSouthboundSignals;

    private int pendingSide = -1;
    private CoordTuple signalBus = null;
    private boolean doUpdate = false;
    private boolean isRegistered = false;

    public SignalTerminalLogic() {
        super();

        for (int i = 0; i < 6; i++) {
            connectedSides[i] = -1;
            receivingSides[i] = -1;
            sideChannel[i] = 0;
            forceUpdateSide = false;
        }

        neighborChanged = false;

    }

    private void tryRegister() {
        boolean wasRegistered = isRegistered;
        if (!(worldObj instanceof World)) {
            return;
        }
        if (!(signalBus instanceof CoordTuple)) {
            return;
        }

        TileEntity te = worldObj.getTileEntity(signalBus.x, signalBus.y, signalBus.z);
        if (!(te instanceof SignalBusLogic)) {
            return;
        }

        isRegistered = ((SignalBusLogic) te).registerTerminal(worldObj, xCoord, yCoord, zCoord, false);

        if (isRegistered != wasRegistered) {
            if (isRegistered) {
                doUpdate = true;

            } else {
                signalBus = null;
                doUpdate = true;
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        sideChannel = data.getByteArray("sideChannel");
        receivingSides = data.getByteArray("receivingSides");
        connectedSides = data.getByteArray("connectedSides");

        if (sideChannel.length != 6) {
            sideChannel = new byte[] { 0, 0, 0, 0, 0, 0 };
        }
        if (receivingSides.length != 6) {
            receivingSides = new byte[] { -1, -1, -1, -1, -1, -1 };
        }
        if (connectedSides.length != 6) {
            connectedSides = new byte[] { -1, -1, -1, -1, -1, -1 };
        }

        int tX = data.getInteger("BusX");
        int tY = data.getInteger("BusY");
        int tZ = data.getInteger("BusZ");

        if (tX == xCoord && tY == yCoord && tZ == zCoord) {
            signalBus = null;
        } else {
            signalBus = new CoordTuple(tX, tY, tZ);
        }

        if (!isRegistered) {
            tryRegister();
        }

        doUpdate = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        data.setByteArray("sideChannel", sideChannel);
        data.setByteArray("receivingSides", receivingSides);
        data.setByteArray("connectedSides", connectedSides);

        if (signalBus != null) {
            data.setInteger("BusX", signalBus.x);
            data.setInteger("BusY", signalBus.y);
            data.setInteger("BusZ", signalBus.z);
        } else {
            data.setInteger("BusX", xCoord);
            data.setInteger("BusY", yCoord);
            data.setInteger("BusZ", zCoord);
        }
    }

    @Override
    public void updateEntity() {
        if (pendingSide >= 0 && pendingSide < 6 && connectedSides[pendingSide] == -1) {
            connectedSides[pendingSide] = 0;
            pendingSide = -1;
            forceUpdateSide = true;
        }

        if (worldObj.isRemote) {
            return;
        }

        if (forceUpdateSide || neighborChanged) {
            checkNeighbors();
            neighborChanged = false;
            forceUpdateSide = false;
        }

        if (!doUpdate) {
            return;
        }

        if (!isRegistered) {
            tryRegister();
        }

        if (cachedSouthboundSignals != null) {
            processSignalUpdate(cachedSouthboundSignals);
        }

        doUpdate = false;

        return;
    }

    // Receive Southbound Signal Updates
    public void receiveSignalUpdate(byte[] signals) {
        if (!Arrays.equals(cachedSouthboundSignals, signals)) {
            cachedSouthboundSignals = signals.clone();
            doUpdate = true;
        }
    }

    private void processSignalUpdate(byte[] signals) {
        int oldValue;
        int targetX = 0;
        int targetY = 0;
        int targetZ = 0;
        int oSide;

        for (int i = 0; i < 6; i++) {
            if (connectedSides[i] != -1) {
                oldValue = connectedSides[i];
                connectedSides[i] = signals[sideChannel[i]];

                if (oldValue != connectedSides[i]) {
                    targetX = xCoord;
                    targetY = yCoord;
                    targetZ = zCoord;
                    switch (i) {
                        case 0:
                            targetY += -1;
                            oSide = 1;
                            break;
                        case 1:
                            targetY += 1;
                            oSide = 0;
                            break;
                        case 2:
                            targetZ += 1;
                            oSide = 2;
                            break;
                        case 3:
                            targetZ += -1;
                            oSide = 3;
                            break;
                        case 4:
                            targetX += -1;
                            oSide = 5;
                            break;
                        case 5:
                            targetX += 1;
                            oSide = 4;
                            break;
                        default:
                            oSide = 0;
                    }

                    worldObj.notifyBlockOfNeighborChange(targetX, targetY, targetZ, MechContent.signalTerminal);
                    worldObj.notifyBlocksOfNeighborChange(targetX, targetY, targetZ, MechContent.signalTerminal, oSide);
                }
            }
        }
    }

    // Callback from Un/Registration
    public void setBusCoords(World world, int x, int y, int z) {
        if (world.provider.dimensionId == worldObj.provider.dimensionId && !world.isRemote && !worldObj.isRemote) {
            signalBus = new CoordTuple(x, y, z);
            world.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
        doUpdate = true;
    }

    public int isProvidingWeakPower(int side) {
        return isProvidingStrongPower(side);
    }

    public int isProvidingStrongPower(int side) {
        int tside = side;

        switch (side) {
            case 0:
                tside = 1;
                break;
            case 1:
                tside = 0;
                break;
            case 2:
                tside = 2;
                break;
            case 3:
                tside = 3;
                break;
            case 4:
                tside = 5;
                break;
            case 5:
                tside = 4;
                break;
            default:
                tside = side;
        }

        if (tside >= 0 && tside < 6 && connectedSides[tside] > 0) {
            return connectedSides[tside] - 1;
        }

        return 0;
    }

    public void addPendingSide(int side) {
        pendingSide = side;
    }

    public void connectPending() {
        if (pendingSide >= 0 && pendingSide < 6 && connectedSides[pendingSide] == -1) {
            connectedSides[pendingSide] = 0;
        }
        pendingSide = -1;
        doUpdate = true;
    }

    public byte[] getConnectedSides() {
        return connectedSides.clone();
    }

    /* Packets */
    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeToNBT(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readFromNBT(packet.func_148857_g());
        markDirty();
        worldObj.func_147479_m(xCoord, yCoord, zCoord);
        this.doUpdate = true;
    }

    public static IIcon getChannelIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te != null && te instanceof SignalTerminalLogic) {
            int channel = ((SignalTerminalLogic) te).sideChannel[side];

            return ((SignalTerminal) TMechworks.content.signalTerminal).getChannelIcon(channel);
        }

        return ((SignalTerminal) TMechworks.instance.content.signalTerminal).getChannelIcon(0);
    }

    public static IIcon[] getChannelIcons() {
        return ((SignalTerminal) TMechworks.content.signalTerminal).channelIcons;
    }

    public static IIcon getChannelIcon(int channel) {
        return getChannelIcons()[channel];
    }

    public IIcon getChannelIconFromLogic(int side) {
        return getChannelIcons()[sideChannel[side]];
    }

    public void nextChannel(int side) {
        if (worldObj.isRemote) {
            return;
        }

        sideChannel[side]++;

        if (sideChannel[side] >= 16) {
            sideChannel[side] = 0;
        }

        doUpdate = true;
    }

    public void prevChannel(int side) {
        if (worldObj.isRemote) {
            return;
        }

        sideChannel[side]--;

        if (sideChannel[side] < 0) {
            sideChannel[side] = 15;
        }

        doUpdate = true;
    }

    public void notifyBreak() {
        if (!(worldObj instanceof World) || worldObj.isRemote) {
            return;
        }
        if (!(signalBus instanceof CoordTuple)) {
            return;
        }

        TileEntity te = worldObj.getTileEntity(signalBus.x, signalBus.y, signalBus.z);
        if (te == null || !(te instanceof SignalBusLogic)) {
            return;
        }

        ((SignalBusLogic) te).unregisterTerminal(worldObj, xCoord, yCoord, zCoord);
    }

    public void onNeighborBlockChange() {
        if (worldObj.isRemote) {
            return;
        }

        neighborChanged = true;
    }

    public void checkNeighbors() {
        if (signalBus != null && signalBus instanceof CoordTuple) {
            TileEntity te = worldObj.getTileEntity(signalBus.x, signalBus.y, signalBus.z);

            if (te instanceof SignalBusLogic) {
                ((SignalBusLogic) te)
                        .updateTransceiverSignals(new CoordTuple(xCoord, yCoord, zCoord), getReceivedSignals());
            }
        } else {
            // We can redistribute signals locally at least
            receiveSignalUpdate(getReceivedSignals());
        }
    }

    public IIcon[] getSideIcons() {
        IIcon[] icons = getChannelIcons();
        IIcon[] sideIcons = new IIcon[6];

        for (int i = 0; i < 6; i++) {
            if (sideChannel[i] > 0) {
                sideIcons[i] = icons[sideChannel[i]];
            } else {
                sideIcons[i] = icons[0];
            }
        }

        return sideIcons;
    }

    // Provide Northbound Signals
    public byte[] getReceivedSignals(boolean vanillaRedstoneKludge) {
        byte[] highChannel = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        int[] offset = new int[] { 0, 0, 0 };
        int oSide;
        int tempStrength;
        byte temporarySide;

        for (int i = 0; i < 6; i++) {
            if (connectedSides[i] != -1) {
                offset[0] = 0;
                offset[1] = 0;
                offset[2] = 0;
                switch (i) {
                    case 0:
                        offset[1] += -1;
                        oSide = 1;
                        break;
                    case 1:
                        offset[1] += 1;
                        oSide = 0;
                        break;
                    case 2:
                        offset[2] += 1;
                        oSide = 3;
                        break;
                    case 3:
                        offset[2] += -1;
                        oSide = 2;
                        break;
                    case 4:
                        offset[0] += -1;
                        oSide = 5;
                        break;
                    case 5:
                        offset[0] += 1;
                        oSide = 4;
                        break;
                    default:
                        oSide = i;
                }

                tempStrength = 0;
                if (connectedSides[i] > 0) {
                    // We are currently powering this side, we need to identify if we are the only source or not
                    temporarySide = connectedSides[i];
                    connectedSides[i] = -1;
                    tempStrength = worldObj
                            .getIndirectPowerLevelTo(xCoord + offset[0], yCoord + offset[1], zCoord + offset[2], oSide);
                    // tempStrength = worldObj.getStrongestIndirectPower(xCoord + offset[0], yCoord + offset[1], zCoord
                    // + offset[2]);
                    if (tempStrength > 0 && tempStrength == (temporarySide - 1)) {
                        // Let's make sure we're not dealing with vanilla redstone

                        Block bid;
                        int power = 0;
                        int temp = 0;

                        for (int j = 0; j < 6; ++j) {
                            if (offset[0] + Facing.offsetsXForSide[j] == 0 && offset[1] + Facing.offsetsYForSide[j] == 0
                                    && offset[2] + Facing.offsetsZForSide[j] == 0) {
                                continue;
                            }
                            temp = worldObj.getIndirectPowerLevelTo(
                                    xCoord + offset[0] + Facing.offsetsXForSide[j],
                                    yCoord + offset[1] + Facing.offsetsYForSide[j],
                                    zCoord + offset[2] + Facing.offsetsZForSide[j],
                                    j);

                            if (temp > power) {

                                bid = worldObj.getBlock(
                                        xCoord + offset[0] + Facing.offsetsXForSide[j],
                                        yCoord + offset[1] + Facing.offsetsYForSide[j],
                                        zCoord + offset[2] + Facing.offsetsZForSide[j]);
                                if (bid != Blocks.redstone_wire) {
                                    power = temp;
                                }
                            }

                            if (power == tempStrength) {
                                // We've found our highest source that's not RS, let's save some cycles
                                break;
                            }
                        }
                        tempStrength = power;

                    }
                    connectedSides[i] = temporarySide;
                } else {
                    tempStrength = worldObj
                            .getIndirectPowerLevelTo(xCoord + offset[0], yCoord + offset[1], zCoord + offset[2], oSide);
                    // tempStrength = worldObj.getStrongestIndirectPower(xCoord + offset[0], yCoord + offset[1], zCoord
                    // + offset[2]);
                }

                // if (tempStrength > 0 && tempStrength < receivingSides[i]){
                // receivingSides[i] = 0;
                // connectedSides[i] = 0;
                // }
                // else {
                // receivingSides[i] = (byte)Math.max((byte)tempStrength, 0);
                // }
                if (tempStrength > 0) {
                    receivingSides[i] = (byte) (tempStrength - 1);
                } else {
                    receivingSides[i] = 0;
                }

                // if ((receivingSides[i] - 1) > connectedSides[i]) {
                // connectedSides[i] = (byte)(receivingSides[i] - 1);
                // }

                if (receivingSides[i] > highChannel[sideChannel[i]]) {
                    highChannel[sideChannel[i]] = (byte) receivingSides[i];
                }

            }
        }

        return highChannel;
    }

    @Override
    public int doUnregister(boolean reHoming) {
        int dropWire = 0;

        if (signalBus != null) {
            TileEntity te = worldObj.getTileEntity(signalBus.x, signalBus.y, signalBus.z);
            dropWire = Math.abs(xCoord - signalBus.x) + Math.abs(yCoord - signalBus.y) + Math.abs(zCoord - signalBus.z);
            if (te instanceof SignalBusLogic) {
                ((SignalBusLogic) te).unregisterTerminal(worldObj, xCoord, yCoord, zCoord);
            }
        }
        // Remove signalBus coords
        signalBus = null;

        // Calculate new local received signals and provide them to local connections
        receiveSignalUpdate(getReceivedSignals());

        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return (reHoming) ? dropWire : 0;
    }

    @Override
    public byte[] getReceivedSignals() {
        return getReceivedSignals(false);
    }

    public int getDroppedTerminals() {
        int count = 0;
        for (int i = 0; i < 6; ++i) {
            if (connectedSides[i] != -1) {
                ++count;
            }
        }

        return count;
    }

    public int getDroppedWire() {
        int calcWire = 0;
        if (signalBus instanceof CoordTuple) {
            calcWire += Math.abs(signalBus.x - xCoord);
            calcWire += Math.abs(signalBus.y - yCoord);
            calcWire += Math.abs(signalBus.z - zCoord);

            return calcWire;
        }
        return 0;
    }

    @Override
    public CoordTuple getBusCoords() {
        return signalBus;
    }
}
