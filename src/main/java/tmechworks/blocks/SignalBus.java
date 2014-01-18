package tmechworks.blocks;

import static net.minecraftforge.common.util.ForgeDirection.DOWN;
import static net.minecraftforge.common.util.ForgeDirection.EAST;
import static net.minecraftforge.common.util.ForgeDirection.NORTH;
import static net.minecraftforge.common.util.ForgeDirection.SOUTH;
import static net.minecraftforge.common.util.ForgeDirection.UP;
import static net.minecraftforge.common.util.ForgeDirection.WEST;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import mantle.world.WorldHelper;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import tmechworks.TMechworks;
import tmechworks.blocks.logic.SignalBusLogic;
import tmechworks.client.block.SignalBusRender;
import tmechworks.lib.TMechworksRegistry;
import tmechworks.lib.multiblock.IMultiblockMember;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class SignalBus extends Block implements ITileEntityProvider {
    public static class BusGeometry {
        public static double cable_width_min = 0.375D;
        public static double cable_width_max = 1 - cable_width_min;
        public static double cable_low_height = 0.2D;
        public static double cable_low_offset = 0.0D;
        
        public static double cable_high_height = 1.0D;
        public static double cable_high_offset = 1 - cable_low_height;
        
        public static double cable_extend_min = 0.0D;
        public static double cable_extend_max = 1.0D;
        
        public static double cable_corner_min = cable_high_offset - 1;
        public static double cable_corner_max = cable_low_height + 1;
        
        public static double zfight = 0.00001D;
    }
    
    public static int HITBOXES = 6;
    
    public IIcon[] icons;
    public String[] textureNames = new String[] { "signalbus" };

	public SignalBus() {
		super(Material.field_151594_q);
        this.func_149711_c(0.1F);
        this.func_149752_b(1);
        this.setStepSound(soundMetalFootstep);
        func_149647_a(TMechworksRegistry.Mechworks);
	}

	@Override
	public void onNeighborTileChange(World world, int x, int y, int z, int tileX, int tileY, int tileZ) {
		TileEntity te = world.getBlockTileEntity(tileX, tileY, tileZ);
		if (te instanceof SignalBusLogic) {
			if (((SignalBusLogic)te).getMultiblockMaster() != null) {
				((SignalBusLogic)te).getMultiblockMaster().detachBlock((IMultiblockMember)te, false);
			}
		}
	}

	@Override
    public void onNeighborBlockChange (World world, int x, int y, int z, Block block)
    {
	    if (block == this)
	    {
	        return;
	    }
	    super.onNeighborBlockChange(world, x, y, z, block);
	    
	    TileEntity te = world.getBlockTileEntity(x, y, z);
	    if (te instanceof SignalBusLogic)
	    {
	        ItemStack tempStack;
	        float jumpX, jumpY, jumpZ;
	        Random rand = new Random();
	        
	        int dropBus = ((SignalBusLogic)te).checkUnsupportedSides();
            if (dropBus > 0)
            {
                if (((SignalBusLogic)te).checkShouldDestroy())
                {
                    WorldHelper.setBlockToAir(world, x, y, z);
                }

                tempStack = new ItemStack(TMechworks.content.signalBus, dropBus, 0);
                jumpX = rand.nextFloat() * 0.8F + 0.1F;
                jumpY = rand.nextFloat() * 0.8F + 0.1F;
                jumpZ = rand.nextFloat() * 0.8F + 0.1F;

                EntityItem entityitem = new EntityItem(world, (double) ((float) x + jumpX), (double) ((float) y + jumpY), (double) ((float) z + jumpZ), tempStack);

                float offset = 0.05F;
                entityitem.motionX = (double) ((float) rand.nextGaussian() * offset);
                entityitem.motionY = (double) ((float) rand.nextGaussian() * offset + 0.2F);
                entityitem.motionZ = (double) ((float) rand.nextGaussian() * offset);
                world.spawnEntityInWorld(entityitem);
                
                world.markBlockForUpdate(x, y, z);
            }
	    }
    }

    @Override
	public void onBlockAdded(World world, int x, int y, int z) {
		TileEntity te = world.getBlockTileEntity(x, y, z);
		if (te != null && te instanceof SignalBusLogic) {
			((SignalBusLogic)te).onBlockAdded(world, x, y, z);
		}
	}

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon (int side, int metadata)
    {
        return icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void func_149651_a (IIconRegister iconRegister)
    {
        this.icons = new IIcon[textureNames.length];

        for (int i = 0; i < this.icons.length; ++i)
        {
            this.icons[i] = iconRegister.registerIcon("tmechworks:" + textureNames[i]);
        }
    }

    @Override
    public boolean func_149686_d ()
    {
        return false;
    }

    @Override
    public boolean isOpaqueCube ()
    {
        return false;
    }

    @Override
    public int getRenderType ()
    {
        return SignalBusRender.renderID;
    }
    
    public void addCollisionBoxesToList (World world, int x, int y, int z, AxisAlignedBB collisionTest, List collisionBoxList, Entity entity)
    {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof SignalBusLogic)
        {
            for (AxisAlignedBB aabb : getBoxes((SignalBusLogic) te))
            {
                if (aabb == null)
                {
                    continue;
                }

                aabb = AxisAlignedBB.getBoundingBox(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
                aabb.minX += x;
                aabb.minY += y;
                aabb.minZ += z;
                aabb.maxX += x;
                aabb.maxY += y;
                aabb.maxZ += z;

                if (collisionTest.intersectsWith(aabb))
                {
                    collisionBoxList.add(aabb);
                }
            }
        }
        else
        {
            super.addCollisionBoxesToList(world, x, y, z, collisionTest, collisionBoxList, entity);
        }
    }
    
    @Override
    @SideOnly(Side.CLIENT)
    public boolean func_149646_a (IBlockAccess par1iBlockAccess, int par2, int par3, int par4, int par5)
    {
        return true;
    }

    @Override
    public MovingObjectPosition collisionRayTrace (World world, int x, int y, int z, Vec3 start, Vec3 end)
    {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof SignalBusLogic)
        {
            MovingObjectPosition closest = null;
            AxisAlignedBB[] boxes = getBoxes((SignalBusLogic)te);
            
            double closestCalc = Double.MAX_VALUE;
            double hitDistance = 0D;

            for (int i = 0; i < boxes.length; i++)
            {
                if (boxes[i] == null)
                {
                    continue;
                }
                this.func_149676_a((float)boxes[i].minX, (float)boxes[i].minY, (float)boxes[i].minZ, (float)boxes[i].maxX, (float)boxes[i].maxY, (float)boxes[i].maxZ);
                MovingObjectPosition hit = super.collisionRayTrace(world, x, y, z, start, end);
                if (hit != null)
                {
                    hitDistance = start.distanceTo(hit.hitVec);
                    if (hitDistance < closestCalc)
                    {
                        closestCalc = hitDistance;
                        closest = hit;
                    }
                }
            }
            return closest;
        }
        
        return null;
    }
    
    private static AxisAlignedBB[] getBoxes (SignalBusLogic logic)
    {
        boolean placed[] = logic.placedSides();
        boolean connected[];
        boolean corners[];
        boolean renderDir[];

        AxisAlignedBB[] parts = new AxisAlignedBB[HITBOXES];

        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;
        boolean didRender = false;
        
        if (placed[ForgeDirection.DOWN.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.DOWN);
            corners = logic.getRenderCorners(ForgeDirection.DOWN);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = (renderDir[ForgeDirection.WEST.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minY = BusGeometry.cable_low_offset;
            minZ = (renderDir[ForgeDirection.NORTH.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            maxX = (renderDir[ForgeDirection.EAST.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxY = BusGeometry.cable_low_height;
            maxZ = (renderDir[ForgeDirection.SOUTH.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            
            parts[0] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (placed[ForgeDirection.UP.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.UP);
            corners = logic.getRenderCorners(ForgeDirection.UP);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = (renderDir[ForgeDirection.WEST.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minY = BusGeometry.cable_high_offset;
            minZ = (renderDir[ForgeDirection.NORTH.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            maxX = (renderDir[ForgeDirection.EAST.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxY = BusGeometry.cable_high_height;
            maxZ = (renderDir[ForgeDirection.SOUTH.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            
            parts[1] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (placed[ForgeDirection.NORTH.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.NORTH);
            corners = logic.getRenderCorners(ForgeDirection.NORTH);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = (renderDir[ForgeDirection.WEST.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minY = (renderDir[ForgeDirection.DOWN.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minZ = BusGeometry.cable_low_offset;
            maxX = (renderDir[ForgeDirection.EAST.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxY = (renderDir[ForgeDirection.UP.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxZ = BusGeometry.cable_low_height;
            
            
            parts[2] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (placed[ForgeDirection.SOUTH.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.SOUTH);
            corners = logic.getRenderCorners(ForgeDirection.SOUTH);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = (renderDir[ForgeDirection.WEST.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minY = (renderDir[ForgeDirection.DOWN.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minZ = BusGeometry.cable_high_offset;
            maxX = (renderDir[ForgeDirection.EAST.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxY = (renderDir[ForgeDirection.UP.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxZ = BusGeometry.cable_high_height;

            parts[3] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (placed[ForgeDirection.WEST.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.WEST);
            corners = logic.getRenderCorners(ForgeDirection.WEST);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = BusGeometry.cable_low_offset;
            minY = (renderDir[ForgeDirection.DOWN.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minZ = (renderDir[ForgeDirection.NORTH.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            maxX = BusGeometry.cable_low_height;
            maxY = (renderDir[ForgeDirection.UP.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxZ = (renderDir[ForgeDirection.SOUTH.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;

            parts[4] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (placed[ForgeDirection.EAST.ordinal()])
        {
            connected = logic.connectedSides(ForgeDirection.EAST);
            corners = logic.getRenderCorners(ForgeDirection.EAST);
            renderDir = new boolean[] {
                    (connected[0] || placed[0] || corners[0]),
                    (connected[1] || placed[1] || corners[1]),
                    (connected[2] || placed[2] || corners[2]),
                    (connected[3] || placed[3] || corners[3]),
                    (connected[4] || placed[4] || corners[4]),
                    (connected[5] || placed[5] || corners[5])
            };
            minX = BusGeometry.cable_high_offset;
            minY = (renderDir[ForgeDirection.DOWN.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            minZ = (renderDir[ForgeDirection.NORTH.ordinal()]) ? BusGeometry.cable_extend_min : BusGeometry.cable_width_min;
            maxX = BusGeometry.cable_high_height;
            maxY = (renderDir[ForgeDirection.UP.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            maxZ = (renderDir[ForgeDirection.SOUTH.ordinal()]) ? BusGeometry.cable_extend_max : BusGeometry.cable_width_max;
            
            parts[5] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            didRender = true;
        }
        if (!didRender)
        {
            minX = BusGeometry.cable_width_min;
            minY = BusGeometry.cable_low_offset;
            minZ = BusGeometry.cable_width_min;
            maxX = BusGeometry.cable_width_max;
            maxY = BusGeometry.cable_low_height;
            maxZ = BusGeometry.cable_width_max;
            
            parts[0] = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        return parts;
    }

    private static int closestClicked (EntityPlayer player, double reachDistance, SignalBusLogic terminal, AxisAlignedBB[] parts)
    {
        int closest = -1;

        Vec3 playerPosition = Vec3.createVectorHelper(player.posX - terminal.field_145851_c, player.posY - terminal.field_145848_d + player.getEyeHeight(), player.posZ - terminal.field_145849_e);
        Vec3 playerLook = player.getLookVec();

        Vec3 playerViewOffset = Vec3.createVectorHelper(playerPosition.xCoord + playerLook.xCoord * reachDistance, playerPosition.yCoord + playerLook.yCoord * reachDistance, playerPosition.zCoord
                + playerLook.zCoord * reachDistance);
        double closestCalc = Double.MAX_VALUE;
        double hitDistance = 0D;

        for (int i = 0; i < parts.length; i++)
        {
            if (parts[i] == null)
            {
                continue;
            }
            MovingObjectPosition hit = parts[i].calculateIntercept(playerPosition, playerViewOffset);
            if (hit != null)
            {
                hitDistance = playerPosition.distanceTo(hit.hitVec);
                if (hitDistance < closestCalc)
                {
                    closestCalc = hitDistance;
                    closest = i;
                }
            }
        }
        return closest;
    }

    /**
     * Draws lines for the edges of the bounding box.
     */
    private static void drawOutlinedBoundingBox(AxisAlignedBB par1AxisAlignedBB)
    {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(3);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.minY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.minY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.minY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.minY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.minY, par1AxisAlignedBB.minZ);
        tessellator.draw();
        tessellator.startDrawing(3);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.minZ);
        tessellator.draw();
        tessellator.startDrawing(1);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.minY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.minY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.minZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.minY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.maxX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.minY, par1AxisAlignedBB.maxZ);
        tessellator.addVertex(par1AxisAlignedBB.minX, par1AxisAlignedBB.maxY, par1AxisAlignedBB.maxZ);
        tessellator.draw();
    }
    
	@Override
	public TileEntity createTileEntity(World world, int metadata) {
		return new SignalBusLogic();
	}

	@Override
	public TileEntity createNewTileEntity(World world) {
		return new SignalBusLogic();
	}
	
	@Override
    public ArrayList<ItemStack> getBlockDropped (World world, int x, int y, int z, int metadata, int fortune)
    {
	    return new ArrayList<ItemStack>();
    }

    @Override
    public void onBlockPlacedBy (World world, int x, int y, int z, EntityLivingBase entityLiving, ItemStack itemStack)
    {
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof SignalBusLogic)
        {
            NBTTagCompound data = itemStack.stackTagCompound;
            if (data != null && data.hasKey("connectedSide"))
            {
                ((SignalBusLogic) te).addPlacedSide(data.getInteger("connectedSide"));
                itemStack.stackTagCompound = null;
            }
        }
    }

    @Override
    public void breakBlock (World world, int x, int y, int z, int id, int meta)
    {
        int dropBus, dropWire = 0;
        float jumpX, jumpY, jumpZ;
        ItemStack tempStack;
        Random rand = new Random();
        
        TileEntity te = world.getBlockTileEntity(x, y, z);
        if (te instanceof SignalBusLogic)
        {
            dropBus = ((SignalBusLogic) te).getDroppedBuses();
            dropWire = ((SignalBusLogic) te).getDroppedWire();
            if (dropBus > 0)
            {
                tempStack = new ItemStack(TMechworks.content.signalBus, dropBus, 0);
                jumpX = rand.nextFloat() * 0.8F + 0.1F;
                jumpY = rand.nextFloat() * 0.8F + 0.1F;
                jumpZ = rand.nextFloat() * 0.8F + 0.1F;

                EntityItem entityitem = new EntityItem(world, (double) ((float) x + jumpX), (double) ((float) y + jumpY), (double) ((float) z + jumpZ), tempStack);

                float offset = 0.05F;
                entityitem.motionX = (double) ((float) rand.nextGaussian() * offset);
                entityitem.motionY = (double) ((float) rand.nextGaussian() * offset + 0.2F);
                entityitem.motionZ = (double) ((float) rand.nextGaussian() * offset);
                world.spawnEntityInWorld(entityitem);
            }
            if (dropWire > 0)
            {
                tempStack = new ItemStack(TMechworks.instance.content.lengthWire, dropWire);
                jumpX = rand.nextFloat() * 0.8F + 0.1F;
                jumpY = rand.nextFloat() * 0.8F + 0.1F;
                jumpZ = rand.nextFloat() * 0.8F + 0.1F;

                EntityItem entityitem = new EntityItem(world, (double) ((float) x + jumpX), (double) ((float) y + jumpY), (double) ((float) z + jumpZ), tempStack);

                float offset = 0.05F;
                entityitem.motionX = (double) ((float) rand.nextGaussian() * offset);
                entityitem.motionY = (double) ((float) rand.nextGaussian() * offset + 0.2F);
                entityitem.motionZ = (double) ((float) rand.nextGaussian() * offset);
                world.spawnEntityInWorld(entityitem);
            }
            ((SignalBusLogic) te).notifyBreak();
        }

        super.breakBlock(world, x, y, z, id, meta);
    }
    
    /**
     * checks to see if you can place this block can be placed on that side of a block: BlockLever overrides
     */
    @Override
    public boolean canPlaceBlockOnSide(World par1World, int par2, int par3, int par4, int par5)
    {
        ForgeDirection dir = ForgeDirection.getOrientation(par5);
        return (dir == DOWN  && par1World.isSideSolid(par2, par3 + 1, par4, DOWN )) ||
               (dir == UP    && par1World.isSideSolid(par2, par3 - 1, par4, UP   )) ||
               (dir == NORTH && par1World.isSideSolid(par2, par3, par4 + 1, NORTH)) ||
               (dir == SOUTH && par1World.isSideSolid(par2, par3, par4 - 1, SOUTH)) ||
               (dir == WEST  && par1World.isSideSolid(par2 + 1, par3, par4, WEST )) ||
               (dir == EAST  && par1World.isSideSolid(par2 - 1, par3, par4, EAST ));
    }

    /**
     * Checks to see if its valid to put this block at the specified coordinates. Args: world, x, y, z
     */
    @Override
    public boolean canPlaceBlockAt(World par1World, int par2, int par3, int par4)
    {
        return par1World.isSideSolid(par2 - 1, par3, par4, EAST ) ||
               par1World.isSideSolid(par2 + 1, par3, par4, WEST ) ||
               par1World.isSideSolid(par2, par3, par4 - 1, SOUTH) ||
               par1World.isSideSolid(par2, par3, par4 + 1, NORTH) ||
               par1World.isSideSolid(par2, par3 - 1, par4, UP   ) ||
               par1World.isSideSolid(par2, par3 + 1, par4, DOWN );
    }

    @Override
    public boolean canBlockStay (World par1World, int par2, int par3, int par4)
    {
        return par1World.isSideSolid(par2 - 1, par3, par4, EAST ) ||
                par1World.isSideSolid(par2 + 1, par3, par4, WEST ) ||
                par1World.isSideSolid(par2, par3, par4 - 1, SOUTH) ||
                par1World.isSideSolid(par2, par3, par4 + 1, NORTH) ||
                par1World.isSideSolid(par2, par3 - 1, par4, UP   ) ||
                par1World.isSideSolid(par2, par3 + 1, par4, DOWN );
    }

    @Override
    public TileEntity func_149915_a (World var1, int var2)
    {
        return new SignalBusLogic();
    }

}
