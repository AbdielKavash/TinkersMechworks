package tmechworks.lib.multiblock;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import mantle.world.CoordTuple;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public abstract class MultiblockMasterBaseLogic
{
    protected World worldObj;
    protected Set<CoordTuple> connectedBlocks;
    protected LinkedList<CoordTuple> pendingRemovalBlocks;
    protected CoordTuple referenceCoord;
    protected CoordTuple minimumCoord;
    protected CoordTuple maximumCoord;
    protected boolean forceRecheck;
    protected boolean forceRevisit;
    protected boolean chunksHaveLoaded;
    protected boolean doUpdates;

    protected MultiblockMasterBaseLogic(World world)
    {
        worldObj = world;
        connectedBlocks = new CopyOnWriteArraySet<CoordTuple>();
        pendingRemovalBlocks = new LinkedList<CoordTuple>();
        referenceCoord = null;

        minimumCoord = new CoordTuple(0, 0, 0);
        maximumCoord = new CoordTuple(0, 0, 0);

        forceRecheck = false;
        chunksHaveLoaded = false;

        doUpdates = true;
    }

    public void restore (NBTTagCompound savedData)
    {
        this.readFromNBT(savedData);
        MultiblockRegistry.register(this);
    }

    public boolean hasBlock (CoordTuple blockCoord)
    {
        return connectedBlocks.contains(blockCoord);
    }

    public void attachBlock (IMultiblockMember member)
    {
        CoordTuple coords = member.getCoordInWorld();
        boolean newMultiblock = this.connectedBlocks.isEmpty();

        if (connectedBlocks.contains(coords))
        {
            return;
        }

        connectedBlocks.add(coords);
        member.onAttached(this);
        this.onBlockAdded(member);
        this.worldObj.markBlockForUpdate(coords.x, coords.y, coords.z);

        if (newMultiblock)
        {
            MultiblockRegistry.register(this);
        }

        if (this.referenceCoord == null)
        {
            referenceCoord = coords;
            member.becomeMultiblockSaveDelegate();
        }
        else if (coords.compareTo(referenceCoord) < 0)
        {
            TileEntity te = this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z);
            ((IMultiblockMember) te).forfeitMultiblockSaveDelegate();

            referenceCoord = coords;
            member.becomeMultiblockSaveDelegate();
        }

        forceRecheck = true;
    }

    protected abstract void onBlockAdded (IMultiblockMember newMember);

    protected abstract void onBlockRemoved (IMultiblockMember oldMember);

    public void detachBlock (IMultiblockMember member, boolean chunkUnloading)
    {
        _detachBlock(member, chunkUnloading);

        if (!chunkUnloading && forceRecheck)
        {
            this.revisitBlocks();
        }
    }

    private void _detachBlock (IMultiblockMember member, boolean chunkUnloading)
    {
        CoordTuple coords = member.getCoordInWorld();
        if (chunkUnloading)
        {
            this.disableUpdates();
        }

        if (connectedBlocks.contains(coords))
        {
            member.onDetached(this);
            connectedBlocks.remove(coords);
            this.onBlockRemoved(member);

            if (referenceCoord != null && referenceCoord.equals(coords))
            {
                member.forfeitMultiblockSaveDelegate();
                referenceCoord = null;
            }
        }

        if (connectedBlocks.isEmpty())
        {
            MultiblockRegistry.unregister(this);
            return;
        }

        if (!forceRecheck && chunkUnloading)
        {
            this.chunksHaveLoaded = true;
        }
        else if (this.chunksHaveLoaded)
        {
            this.chunksHaveLoaded = chunkUnloading;
        }

        forceRecheck = true;

        if (referenceCoord == null)
        {
            if (!this.connectedBlocks.isEmpty())
            {
                for (CoordTuple tcoord : connectedBlocks)
                {
                    TileEntity te = this.worldObj.getTileEntity(tcoord.x, tcoord.y, tcoord.z);
                    if (te == null)
                    {
                        continue;
                    }

                    if (referenceCoord == null)
                    {
                        referenceCoord = tcoord;
                    }
                    else if (tcoord.compareTo(referenceCoord) < 0)
                    {
                        referenceCoord = tcoord;
                    }
                }
            }

            if (referenceCoord != null)
            {
                TileEntity te = this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z);
                ((IMultiblockMember) te).becomeMultiblockSaveDelegate();
            }
        }
    }

    private void disableUpdates ()
    {
        doUpdates = false;
    }

    private void enableUpdates ()
    {
        doUpdates = true;
    }

    private boolean getUpdateState ()
    {
        return doUpdates;
    }

    public void beginMerging ()
    {
    }

    public void merge (MultiblockMasterBaseLogic other)
    {
        if (this.referenceCoord.compareTo(other.referenceCoord) >= 0)
        {
            throw new IllegalArgumentException("You're doing it wrong");
        }

        TileEntity te;
        Set<CoordTuple> acquiredMembers = new CopyOnWriteArraySet<CoordTuple>(other.connectedBlocks);

        other.onMergedIntoOtherMaster(this);

        IMultiblockMember acquiredMember;
        for (CoordTuple coord : acquiredMembers)
        {
            this.connectedBlocks.add(coord);
            te = this.worldObj.getTileEntity(coord.x, coord.y, coord.z);
            acquiredMember = (IMultiblockMember) te;
            acquiredMember.onMasterMerged(this);
            this.onBlockAdded(acquiredMember);
        }
    }

    private void onMergedIntoOtherMaster (MultiblockMasterBaseLogic newMaster)
    {
        this.disableUpdates();

        if (referenceCoord != null)
        {
            TileEntity te = this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z);
            if (te instanceof IMultiblockMember)
            {
                ((IMultiblockMember) te).forfeitMultiblockSaveDelegate();
            }
            this.referenceCoord = null;
        }

        this.connectedBlocks.clear();
        MultiblockRegistry.unregister(this);

        this.onDataMerge(newMaster);
    }

    protected abstract void onDataMerge (MultiblockMasterBaseLogic newMaster);

    public void endMerging ()
    {
    }

    public final void doMultiblockTick ()
    {
        if (forceRevisit)
        {
            CoordTuple deadCoords;

            forceRevisit = false;
            while (!pendingRemovalBlocks.isEmpty())
            {
                deadCoords = pendingRemovalBlocks.removeFirst();
                if (connectedBlocks.contains(deadCoords))
                {
                    if (referenceCoord.equals(deadCoords))
                    {
                        referenceCoord = null;
                    }
                    connectedBlocks.remove(deadCoords);
                }
            }
            if (referenceCoord == null)
            {
                if (!this.connectedBlocks.isEmpty())
                {
                    for (CoordTuple tcoord : connectedBlocks)
                    {
                        TileEntity te = this.worldObj.getTileEntity(tcoord.x, tcoord.y, tcoord.z);
                        if (!(te instanceof IMultiblockMember))
                        {
                            continue;
                        }

                        if (referenceCoord == null)
                        {
                            referenceCoord = tcoord;
                        }
                        else if (tcoord.compareTo(referenceCoord) < 0)
                        {
                            referenceCoord = tcoord;
                        }
                    }
                }

                if (referenceCoord != null)
                {
                    TileEntity te = this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z);
                    ((IMultiblockMember) te).becomeMultiblockSaveDelegate();
                }
            }

            this.revisitBlocks();

            forceRecheck = true;
        }

        if (this.connectedBlocks.isEmpty())
        {
            MultiblockRegistry.unregister(this);
            return;
        }

        if (this.forceRecheck)
        {
            // Recheck structure
            this.doStructureCheck();
            this.forceRecheck = false;
        }

        if (this.doUpdates)
        {
            if (doUpdate())
            {
                // Force save delegate to update data
            }
        }
    }

    // Override to provide sanity checking on structure
    public void doStructureCheck ()
    {
    }

    // Return true to force chunk saving
    public boolean doUpdate ()
    {
        return false;
    }

    public void revisitBlocks ()
    {
        TileEntity te;
        // Ensure that our current reference coords are valid. If not, invalidate it.
        if (referenceCoord != null && this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z) == null)
        {
            referenceCoord = null;
        }

        // Reset visitations and find the minimum coordinate
        for (CoordTuple coord : connectedBlocks)
        {

            if (!this.worldObj.getChunkProvider().chunkExists(coord.x >> 4, coord.z >> 4))
            {
                continue;
            }

            te = this.worldObj.getTileEntity(coord.x, coord.y, coord.z);
            if (!(te instanceof IMultiblockMember))
            {
                continue;
            } // this happens during chunk unload; ignore it

            ((IMultiblockMember) te).setUnivisted();

            if (referenceCoord == null)
            {
                referenceCoord = coord;
            }
            else if (coord.compareTo(referenceCoord) < 0)
            {
                referenceCoord = coord;
            }
        }

        if (referenceCoord == null)
        {
            // There are no valid parts remaining. This is due to a chunk unload.
            return;
        }

        // Now visit all connected parts, breadth-first, starting from reference coord.
        LinkedList<IMultiblockMember> membersToCheck = new LinkedList<IMultiblockMember>();
        IMultiblockMember[] nearbyMembers = null;
        IMultiblockMember member = (IMultiblockMember) this.worldObj.getTileEntity(referenceCoord.x, referenceCoord.y, referenceCoord.z);

        membersToCheck.add(member);
        while (!membersToCheck.isEmpty())
        {
            member = membersToCheck.removeFirst();
            member.setVisited();

            nearbyMembers = member.getNeighboringMembers();
            for (IMultiblockMember nearbyMember : nearbyMembers)
            {
                // Ignore different machines
                if (nearbyMember.getMultiblockMaster() != this)
                {
                    continue;
                }

                if (!nearbyMember.isVisited())
                {
                    nearbyMember.setVisited();
                    membersToCheck.add(nearbyMember);
                }
            }
        }

        // First, remove any blocks that are still disconnected
        List<IMultiblockMember> orphans = new LinkedList<IMultiblockMember>();
        List<CoordTuple> deadBlocks = new ArrayList<CoordTuple>();
        for (CoordTuple coord : connectedBlocks)
        {
            if (!this.worldObj.getChunkProvider().chunkExists(coord.x >> 4, coord.z >> 4))
            {
                deadBlocks.add(coord);
                continue;
            }
            member = (IMultiblockMember) this.worldObj.getTileEntity(coord.x, coord.y, coord.z);
            if (!member.isVisited())
            {
                orphans.add(member);
            }
        }

        this.connectedBlocks.removeAll(deadBlocks);

        // Remove all orphaned parts. i.e. Actually orphan them
        for (IMultiblockMember orphan : orphans)
        {
            this._detachBlock(orphan, false);
        }

        // Now go through and start up as many new masters as possible
        for (IMultiblockMember orphan : orphans)
        {
            if (!orphan.isConnected())
            {
                orphan.onOrphaned();
            }
        }
    }

    public CoordTuple getReferenceCoord ()
    {
        return referenceCoord;
    }

    public int getNumConnectedBlocks ()
    {
        return connectedBlocks.size();
    }

    public abstract void writeToNBT (NBTTagCompound data);

    public abstract void readFromNBT (NBTTagCompound data);

    public abstract void formatDescriptionPacket (NBTTagCompound data);

    public abstract void decodeDescriptionPacket (NBTTagCompound data);

    public void scheduleRemoveAndRevisit (IMultiblockMember remove)
    {
        forceRevisit = true;
        pendingRemovalBlocks.add(remove.getCoordInWorld());
    }
}
