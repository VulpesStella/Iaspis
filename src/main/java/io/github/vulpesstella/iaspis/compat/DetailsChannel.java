package io.github.vulpesstella.iaspis.compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;
import io.github.vulpesstella.iaspis.Iaspis;
import io.github.vulpesstella.iaspis.api.MultipartPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Read-only detail and machine snapshot channel. */
public final class DetailsChannel {
    public static final String NAME="IaspisDetails";
    public static final int MAX_BYTES=30000;
    public static final int MAX_SOURCES=32;
    private static final Map<EntityPlayerMP,Long> REQUESTS=new WeakHashMap<EntityPlayerMP,Long>();

    public static boolean available(EntityPlayer player) {
        return player != null && NetworkRegistry.instance().isChannelActive(NAME,(Player)player);
    }

    public static void request(Target target,int sequence,boolean detailed,List<String> ids,String machine) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(bytes);
            MovingObjectPosition hit=target.getPosition();
            MultipartPart part=target.getMultipartPart();
            out.writeByte(1);out.writeInt(sequence);out.writeInt(target.getPlayer().dimension);
            out.writeInt(hit.blockX);out.writeInt(hit.blockY);out.writeInt(hit.blockZ);out.writeInt(target.getMetadata());
            out.writeInt(part==null?-1:part.getIndex());out.writeUTF(part==null?"":part.getType());
            out.writeBoolean(detailed);out.writeByte(Math.min(MAX_SOURCES,ids.size()));
            for(int i=0;i<Math.min(MAX_SOURCES,ids.size());i++)out.writeUTF(ids.get(i));
            out.writeBoolean(machine!=null);if(machine!=null)out.writeUTF(machine);
            if(bytes.size()<=MAX_BYTES)PacketDispatcher.sendPacketToServer(new Packet250CustomPayload(NAME,bytes.toByteArray()));
        } catch(IOException e){WailaBridge.INSTANCE.report("Could not encode detail request",e);}
    }

    public static void receive(INetworkManager manager,Packet250CustomPayload packet,Player player)throws IOException {
        if(packet.data==null || packet.data.length>MAX_BYTES || !available((EntityPlayer)player))return;
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(packet.data));
        if(in.readUnsignedByte()!=1)return;
        int sequence=in.readInt();
        if(!(player instanceof EntityPlayerMP)) {
            NBTTagCompound data=CompressedStreamTools.read(in);
            if(in.available()!=0)throw new IOException("Unexpected detail response tail");
            Iaspis.proxy.receiveDetails(manager,sequence,data);
            return;
        }
        EntityPlayerMP sender=(EntityPlayerMP)player;
        if(!permit(sender))return;
        int dimension=in.readInt(),x=in.readInt(),y=in.readInt(),z=in.readInt(),metadata=in.readInt();
        int partIndex=in.readInt();String partType=in.readUTF();
        boolean detailed=in.readBoolean();int count=in.readUnsignedByte();
        if(count>MAX_SOURCES || partType.length()>128 || partIndex< -1)throw new IOException("Invalid detail selection");
        Set<String> ids=new LinkedHashSet<String>();
        for(int i=0;i<count;i++){
            String id=in.readUTF();if(!validId(id))throw new IOException("Invalid detail source ID");ids.add(id);
        }
        String machine=in.readBoolean()?in.readUTF():null;
        if(machine!=null && !machine.isEmpty() && !validId(machine))throw new IOException("Invalid machine source ID");
        if(in.available()!=0)throw new IOException("Unexpected detail request tail");
        Target target=resolve(sender,dimension,x,y,z,metadata,partIndex,partType);
        if(target==null)return;
        NBTTagCompound response=collect(target,detailed,ids,machine);
        send(sender,sequence,response);
    }

    static boolean validId(String id){return id.length()<=64 && id.matches("[a-z0-9_]+:[a-z0-9_.-]+");}

    private static synchronized boolean permit(EntityPlayerMP player){
        long now=System.nanoTime();Long last=REQUESTS.get(player);
        if(last!=null && now-last<150000000L)return false;
        REQUESTS.put(player,now);return true;
    }

    static Target resolve(EntityPlayerMP player,int dimension,int x,int y,int z,int metadata,int index,String type){
        if(player.dimension!=dimension || y<0 || y>=player.worldObj.getHeight()
                || player.getDistanceSq(x+.5,y+.5,z+.5)>64 || !player.worldObj.blockExists(x,y,z))return null;
        if(player.worldObj.getBlockMetadata(x,y,z)!=metadata || player.worldObj.getBlockTileEntity(x,y,z)==null)return null;
        Vec3 eyes=Vec3.createVectorHelper(player.posX,player.posY+player.getEyeHeight(),player.posZ);
        Vec3 look=player.getLook(1);
        MovingObjectPosition hit=player.worldObj.rayTraceBlocks_do_do(eyes,eyes.addVector(look.xCoord*8,look.yCoord*8,look.zCoord*8),false,false);
        if(hit==null || hit.entityHit!=null || hit.blockX!=x || hit.blockY!=y || hit.blockZ!=z)return null;
        Target target=new Target(player.worldObj,player,player,hit,1,null);
        MultipartPart part=target.getMultipartPart();
        if(WailaBridge.isMultipart(target.getBlock()) && part==null)return null;
        if(part==null ? index!=-1 || !type.isEmpty() : index!=part.getIndex() || !type.equals(part.getType()))return null;
        return target;
    }

    static NBTTagCompound collect(Target target,boolean detailed,Collection<String> ids,String machineId)throws IOException {
        NBTTagCompound response=new NBTTagCompound(),details=new NBTTagCompound();
        response.setTag("details",details);
        int count=0;
        for(String id:ids){
            if(++count>MAX_SOURCES)break;
            NBTTagCompound value=DetailRegistry.collect(id,target,detailed);
            if(value==null)continue;
            details.setTag(id,value);
            if(size(response)>MAX_BYTES)details.removeTag(id);
        }
        MachineProviders.Source source=MachineProviders.source(target.getTileEntity());
        String actual=source==null?null:source.id==null?"":source.id;
        if(machineId!=null && machineId.equals(actual)) {
            MachinePreview preview=MachineProviders.collect(target.getTileEntity(),source,detailed);
            if(!preview.isEmpty()) {
                NBTTagCompound machine=encodeMachine(actual,preview);response.setTag("machine",machine);
                if(size(response)>MAX_BYTES)response.removeTag("machine");
            }
        }
        return response;
    }

    public static NBTTagCompound encodeMachine(String id,MachinePreview preview){
        NBTTagCompound tag=new NBTTagCompound();tag.setString("id",id);
        tag.setString("unit",preview.unit==null?"":preview.unit.name());
        tag.setDouble("stored",preview.stored);tag.setDouble("capacity",preview.capacity);tag.setFloat("progress",preview.progress);
        tag.setDouble("input",preview.maxInput);tag.setDouble("output",preview.maxOutput);return tag;
    }
    public static MachinePreview decodeMachine(NBTTagCompound tag)throws IOException {
        MachinePreview.EnergyUnit unit=null;
        try{if(!tag.getString("unit").isEmpty())unit=MachinePreview.EnergyUnit.valueOf(tag.getString("unit"));}
        catch(IllegalArgumentException e){throw new IOException("Unknown projected machine unit");}
        return new MachinePreview(unit,tag.getDouble("stored"),tag.getDouble("capacity"),tag.getFloat("progress"),
                tag.hasKey("input")?tag.getDouble("input"):-1,tag.hasKey("output")?tag.getDouble("output"):-1);
    }
    private static int size(NBTTagCompound tag)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        CompressedStreamTools.write(tag,new DataOutputStream(bytes));return bytes.size()+5;
    }
    private static void send(EntityPlayerMP player,int sequence,NBTTagCompound data)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(1);out.writeInt(sequence);CompressedStreamTools.write(data,out);
        if(bytes.size()<=MAX_BYTES)PacketDispatcher.sendPacketToPlayer(new Packet250CustomPayload(NAME,bytes.toByteArray()),(Player)player);
    }
    private DetailsChannel(){}
}
